package com.bitwise.bitmarket.market

import java.util.Currency
import scala.annotation.tailrec

import com.bitwise.bitmarket.common.currency.FiatAmount

/** Represents a snapshot of a continuous double auction (CDA) */
case class OrderBook(
  currency: Currency,
  bids: Seq[Bid],
  asks: Seq[Ask]) {

  requireSameCurrency()
  requireSortedOrders()
  requireSingleOrderPerRequester()

  def orders: Seq[Order] = bids ++ asks

  /** Tells if a transaction is possible with current orders. */
  def hasCross: Boolean = spread match {
    case (Some(highestBid), Some(lowestAsk)) if highestBid.amount >= lowestAsk.amount => true
    case _ => false
  }

  /** Get current spread (interval between the highest bet price to the lowest bid price */
  def spread: (Option[FiatAmount], Option[FiatAmount]) =
    (bids.headOption.map(_.price), asks.headOption.map(_.price))

  /** Place a new order.
    *
    * Note that a preexisting order by the same requester will be replaced by the new one.
    *
    * @param order  Ask or Bid to place
    * @return       New order book
    */
  def placeOrder(order: Order): OrderBook = {
    val (newBid, newAsk) = order match {
      case bid: Bid => (Some(bid), None)
      case ask: Ask => (None, Some(ask))
    }
    copy(
      bids = (bids.filter(_.requester != order.requester) ++ newBid).sorted,
      asks = (asks.filter(_.requester != order.requester) ++ newAsk).sorted
    )
  }

  /** Clear the market by crossing bid and ask orders
    *
    * @return Cleared market and a sequence of crosses
    */
  def clearMarket: (OrderBook, Seq[Cross]) = clearMarket(bids, asks, Seq.empty)

  @tailrec
  private def clearMarket(
      bids: Seq[Bid], asks: Seq[Ask], crosses: Seq[Cross]): (OrderBook, Seq[Cross]) = {
    (bids.headOption, asks.headOption) match {
      case (Some(bid), Some(ask)) if bid.price.amount >= ask.price.amount =>
        val (cross, remainingBid, remainingAsk) = crossOrders(bid, ask)
        clearMarket(
          remainingBid.toList ++ bids.tail,
          remainingAsk.toList ++ asks.tail,
          crosses :+ cross
        )
      case _ => (OrderBook(currency, bids, asks), crosses)
    }
  }

  private def crossOrders(bid: Bid, ask: Ask): (Cross, Option[Bid], Option[Ask]) = {
    val crossedAmount = bid.amount min ask.amount
    val remainingBid =
      if (bid.amount > crossedAmount) Some(bid.copy(amount = bid.amount - crossedAmount)) else None
    val remainingAsk =
      if (ask.amount > crossedAmount) Some(ask.copy(amount = ask.amount - crossedAmount)) else None
    val cross = Cross(
      amount = crossedAmount,
      price = (bid.price + ask.price) / 2,
      buyer = bid.requester,
      seller = ask.requester
    )
    (cross, remainingBid, remainingAsk)
  }

  private def requireSameCurrency() {
    val otherCurrency: Option[Currency] = orders.map(_.price.currency).find(_ != currency)
    require(
      otherCurrency.isEmpty,
      s"A currency (${otherCurrency.get}) other than $currency was used"
    )
  }

  private def requireSortedOrders() {
    require(bids.sorted == bids, "Bids must be sorted")
    require(asks.sorted == asks, "Asks must be sorted")
  }

  private def requireSingleOrderPerRequester() {
    val requestersWithMultipleOrders = for {
      (requester, orders) <- orders.groupBy(_.requester)
      if orders.size > 1
    } yield requester
    require(requestersWithMultipleOrders.isEmpty,
      "Requesters with multiple orders: " + requestersWithMultipleOrders.mkString(", "))
  }
}

object OrderBook {
  def empty(currency: Currency) = OrderBook(currency, List.empty, List.empty)
}

