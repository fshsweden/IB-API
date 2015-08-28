package examples;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import ib.ApiController.IOrderHandler;
import ib.NewContract;
import ib.NewOrder;
import ib.NewOrderState;
import ib.OrderStatus;
import ib.OrderType;
import ib.Types.Action;
import ib.Types.TimeInForce;

/*
 * IBOrder is a NewOrder plus
 * 
 * 	->	NewContract
 * 	->	OrderStatus
 * 	->	Execution info:
 * 		filled/remaining/avgFillPrice/permid/parentid/lastFillPrice/clientId/whyHeld
 * 	->	eventual errors
 * 
 */
public class IBOrder extends NewOrder {

	/*
	 * Our state (the one that counts!)
	 */
	public enum IBOrderStatus {None, InMarket, InMarketPartlyFilled, Filled, Cancelled};
	private IBOrderStatus order_status = IBOrderStatus.None;
	public IBOrderStatus getOrderStatus() {
		return order_status;
	}
	
	/*
	 * IB State (for reference only)
	 */
	private NewOrderState orderState;
	
	private NewContract contract;

	private IBOrder parent = null;
	
	// status
	private OrderStatus m_status;  // IB Last Status
	private int m_filled;
	private int m_remaining;
	private double m_avgFillPrice;
	private long m_permId;
	private int m_parentId;
	private double m_lastFillPrice;
	private int m_clientId;
	private String m_whyHeld;

	private List<Pair<Integer, String>> errors = new ArrayList<Pair<Integer, String>>();

	public interface ISmartOrderStatusListener {
		public void orderStatusChanged(IBOrder order, OrderStatus status);
		public void orderError(IBOrder order, int errorCode, String errorMsg);
	}

	public IBOrder(NewContract ct, int order_id, String account, Action action, OrderType ot, Double price, int volume, TimeInForce tif, IBOrder parent) {
		super();

		this.contract = ct;
		account(account);
		action(action);
		orderType(ot);
		
		switch (ot) {
			case STP:
				auxPrice(price);
				lmtPrice(0);
			break;
			case STP_LMT:
				auxPrice(price);
				lmtPrice(price);
			break;
			default:
			case LMT:
				auxPrice(0.0);
				lmtPrice(price);
			break;
		}
		totalQuantity(volume);

		tif(tif);
		orderId(order_id);
		this.parent = parent;
		transmit(true);
	}

	public IBOrder(NewContract ct, int order_id, String account, Action action, OrderType ot, Double price, int volume, TimeInForce tif, IBOrder parent, Boolean transmit) {
		super();

		this.contract = ct;
		account(account);
		action(action);
		orderType(ot);
		
		switch (ot) {
			case STP:
				auxPrice(price);
				lmtPrice(0);
			break;
			case STP_LMT:
				auxPrice(price);
				lmtPrice(price);
			break;
			default:
			case LMT:
				auxPrice(0.0);
				lmtPrice(price);
			break;
		}
		totalQuantity(volume);

		tif(tif);
		orderId(order_id);
		this.parent = parent;
		transmit(transmit);
	}
	
	public void setOrderState(NewOrderState os) {
		orderState = os;
	}

	public NewOrderState getOrderState() {
		return orderState;
	}

	private void logMsg(String str) {
		
	}
	
	private void logErr(String str) {
		
	}
	
	private List<ISmartOrderStatusListener> listeners = new ArrayList<ISmartOrderStatusListener>();

	/*
	 * 
	 */
	public void addListener(ISmartOrderStatusListener listener) {
		listeners.add(listener);
	}

	/*	----------------------------------------------------------------------------
	 *	submit an order using supplied IBController
	 *
	 * 	We are only interested in the first message that tells us whether
	 * 	the order has been successfully sent to the market (syntax-checked)
	 * 
	 * 	PreSubmitted is interesting since STP orders get this message until they 
	 * 	are Submitted to the market, but for us it doesnt matter.
	 * 
	 *		
	 *	---------------------------------------------------------------------------- 
	 */
	public int submit(IBController ctrl, Boolean use_latch) {

		AtomicBoolean answer_arrived = new AtomicBoolean(false);
	
		/* if we have a parent, take its order id (we assume it has been placed before us! */
		if (parent != null) {
			parentId(parent.orderId());
		}
		
		/*
		 * We only wait for one of two situations:
		 * A) The order is placed OK, since we got a SUBMITTED callback
		 * B) There was an error!
		 * 
		 * The returns value is the allocated client order id, it doesnt tell us anything really
		 */
		int oid = ctrl.placeOrModifyOrder(contract, this, new IOrderHandler() {

			@Override
			public void orderStatus(
				OrderStatus pStatus, 
				int pFilled, 
				int pRemaining, 
				double pAvgFillPrice, 
				long pPermId, 
				int pParentId, 
				double pLastFillPrice, 
				int pClientId, 
				String pWhyHeld) 
			{
				// reset
				logMsg("placeOrModifyOrder - orderid:" + orderId() + " (" + pClientId + ") " + pPermId + " orderstatus : " + pStatus.name());
				
				m_status = pStatus;
				m_filled = pFilled;
				m_remaining = pRemaining;
				m_avgFillPrice = pAvgFillPrice;
				m_permId = pPermId;
				m_parentId = pParentId;
				m_lastFillPrice = pLastFillPrice;
				m_clientId = pClientId;
				m_whyHeld = pWhyHeld;

				// update our order fields
				IBOrder.this.permId(pPermId);
				
				switch (pStatus) {
					case ApiCancelled:
					case ApiPending:
					case PendingCancel:
					case PendingSubmit:
					case Unknown:
					break;

					case PreSubmitted:
						// For example a STP order!
						if (use_latch) {
							answer_arrived.set(true);
						}
						order_status = IBOrderStatus.InMarket;
						sendToAllListeners(IBOrder.this, pStatus);
						
						// NEW UNTESTED!
						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;
					
					case Submitted:
						if (use_latch) {
							answer_arrived.set(true);
						}
						order_status = IBOrderStatus.InMarket;
						sendToAllListeners(IBOrder.this, pStatus);
						
						// NEW UNTESTED!

						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;

					case Cancelled:
						if (use_latch) {
							answer_arrived.set(true);
						}
						order_status = IBOrderStatus.Cancelled;
						sendToAllListeners(IBOrder.this, pStatus);
						
						// NEW UNTESTED!
						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;

					case Filled:
						if (pRemaining == 0)
							order_status = IBOrderStatus.Filled;
						else
							order_status = IBOrderStatus.InMarketPartlyFilled;
						
						if (use_latch) {
							answer_arrived.set(true);
						}
						
						sendToAllListeners(IBOrder.this, pStatus);
						
						// NEW UNTESTED!
						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;

					case Inactive:
						order_status = IBOrderStatus.None;
						
						// NEW UNTESTED!
						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;

					default:
					break;
				}
			}

			@Override
			public void orderState(NewOrderState orderState) {
				// ignore this one
				switch (orderState.status()) {
					case ApiCancelled:
					case ApiPending:
					case PendingCancel:
					case PendingSubmit:
					case Unknown:
						break;
						
					case PreSubmitted:
						// For example a STP order!
						if (use_latch) {
							answer_arrived.set(true);
						}
						order_status = IBOrderStatus.InMarket;
						sendToAllListeners(IBOrder.this, orderState.status());
						
						// NEW UNTESTED!
						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;
					
					case Submitted:
						if (use_latch) {
							answer_arrived.set(true);
						}
						order_status = IBOrderStatus.InMarket;
						sendToAllListeners(IBOrder.this, orderState.status());
						
						// NEW UNTESTED!

						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;
					
					case Cancelled:
						if (use_latch) {
							answer_arrived.set(true);
						}
						order_status = IBOrderStatus.Cancelled;
						sendToAllListeners(IBOrder.this, orderState.status());
						
						// NEW UNTESTED!
						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;
					
//					case Filled:
//						if (pRemaining == 0)
//							order_status = IBOrderStatus.Filled;
//						else
//							order_status = IBOrderStatus.InMarketPartlyFilled;
//						
//						if (use_latch) {
//							answer_arrived.set(true);
//						}
//						
//						sendToAllListeners(IBOrder.this, pStatus);
//						
//						// NEW UNTESTED!
//						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
//					break;
					
					case Inactive:
						order_status = IBOrderStatus.None;
						
						// NEW UNTESTED!
						ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
					break;
					
					default:
						break;
					
				}
			}

			@Override
			public void handle(int errorCode, String errorMsg) {
				order_status = IBOrderStatus.Cancelled;
				logMsg("placeOrModifyOrder: errorHandler() : orderId:" + orderId() + " error:" + errorCode + " msg:" + errorMsg);
				storeError(errorCode, errorMsg);
				switch (errorCode) {
					default:
						sendErrorToAllListeners(IBOrder.this, errorCode, errorMsg);
					break;
				}
				if (use_latch) {
					answer_arrived.set(true);
				}
				// ctrl.removeOrderHandler(IBOrder.this, this); // Funny and weird code!
			}
		});

		
		if (use_latch) {
			while (!answer_arrived.get()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		switch (order_status) {
			case Cancelled:
				return -1;
			case InMarket:
			case InMarketPartlyFilled:
			case Filled:
				orderId(oid);
				return oid;
			default:
			case None:
				return -1;
		}
	}
	
	public Boolean cancelOrder(IBController ctrl) {
		if (getOrderStatus() == IBOrderStatus.InMarket || getOrderStatus() == IBOrderStatus.InMarketPartlyFilled) {
			ctrl.cancelOrder(orderId());
			return true;
		}
		else {
			return false;
		}
	}
	

	private void storeError(int code, String msg) {
		Pair<Integer, String> p = new Pair<Integer, String>(code, msg);
		errors.add(p);
	}

	/*
	 * 
	 * 
	 */
	public void sendToAllListeners(IBOrder o, OrderStatus s) {
		for (ISmartOrderStatusListener l : listeners) {
			l.orderStatusChanged(o, s);
		}
	}

	public void sendErrorToAllListeners(IBOrder o, int errorCode, String errorMsg) {
		for (ISmartOrderStatusListener l : listeners) {
			l.orderError(o, errorCode, errorMsg);
		}
	}

	public NewContract getContract() {
		return contract;
	}

	public OrderStatus getStatus() {
		return m_status;
	}

	public int getFilled() {
		return m_filled;
	}

	public int getRemaining() {
		return m_remaining;
	}

	public double getAvgFillPrice() {
		return m_avgFillPrice;
	}

	public long getPermId() {
		return m_permId;
	}

	public int getParentId() {
		return m_parentId;
	}

	public double getLastFillPrice() {
		return m_lastFillPrice;
	}

	public int getClientId() {
		return m_clientId;
	}

	public String getWhyHeld() {
		return m_whyHeld;
	}

	public List<Pair<Integer, String>> getErrors() {
		return errors;
	}

	public List<ISmartOrderStatusListener> getListeners() {
		return listeners;
	}

	public void setStatus(OrderStatus m_status) {
		this.m_status = m_status;
	}
}
