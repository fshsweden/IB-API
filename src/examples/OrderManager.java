package examples;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import examples.IBBracketOrder.PositionType;
import examples.IBBracketOrder.WHICH_ORDER;
import ib.ApiController.ILiveOrderHandler;
import ib.ApiController.IPositionHandler;
import ib.ApiController.ITradeReportHandler;
import ib.CommissionReport;
import ib.Execution;
import ib.NewContract;
import ib.NewOrder;
import ib.NewOrderState;
import ib.OrderStatus;
import ib.OrderType;
import ib.Types.Action;
import ib.Types.TimeInForce;

/*	-----------------------------------------------------------------------------------------------------------------
 *	
 * 	
 * 	
 * 	
 *	----------------------------------------------------------------------------------------------------------------- 
 */
public class OrderManager implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6852683630430213439L;

	private transient final IBController				ctrl;
	
	private String							account;
	private Map<Integer, IBOrder>			orders					= new HashMap<Integer, IBOrder>();
	private Map<String, IBExecution>			executions				= new HashMap<String, IBExecution>();
	private Map<Integer, IBBracketOrder>		bracket_orders			= new HashMap<Integer, IBBracketOrder>();
	private List<ITradingEventHandler>		trading_event_handlers	= new ArrayList<ITradingEventHandler>();

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	public OrderManager(IBController ibController, String account) {

		this.account = account;
		this.ctrl = ibController;
	}

	public void Save(String filename) {
		try {
			FileOutputStream fileOut = new FileOutputStream(filename);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this);
			out.close();
			fileOut.close();
			System.out.printf("Serialized data is saved in " + filename);
		} catch (IOException i) {
			i.printStackTrace();
		}
	}

	public void Load(String filename) {
		
	}

	private void logMsg(String str) {
		
	}

	private void logErr(String str) {
		
	}

	public void start() {
		requestLiveOrders();
		requestExecutions();
		requestPositions();
	}

	/*
	 * --------------------------------------------------------------------------
	 * --------------------------------------------------------------------------
	 */
	public void subscribeToTradingEvents(ITradingEventHandler ouh) {

		trading_event_handlers.add(ouh);

		// send all active orders
		for (IBOrder o : orders.values()) {
			for (ITradingEventHandler eh : trading_event_handlers) {
				eh.orderAdded(o);
			}
		}

		for (IBExecution ex : executions.values()) {
			for (ITradingEventHandler eh : trading_event_handlers) {
				IBOrder order = orders.get(ex.m_orderId);
				eh.tradeAdded(order, ex);
			}
		}
	}

	public void unSubscribeToOrderUpdates(ITradingEventHandler ouh) {
		trading_event_handlers.remove(ouh);
	}

	/*
	 * --------------------------------------------------------------------------
	 * placeOrderIB() : only to get a response when placing the order, not
	 * keeping it updated!
	 * --------------------------------------------------------------------------
	 */
	public IBOrder placeOrderIB(NewContract contract, Action action, int qty, OrderType typ, TimeInForce tif, double price, String acct, String ref) {
		final IBOrder order = new IBOrder(contract, 0 /* 0 = new order */, account, action, typ, price, qty, TimeInForce.DAY, null);
		order.orderRef(ref);

		// LOCK() order database!
		int orderId = order.submit(ctrl, true);

		// order.orderId(orderId); already set by submit(), allocated by IBController

		storeIBOrder(order); // store it in case it immediately executes!

		// UNLOCK order database!

		return order;
	}

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	public Integer sendBracketOrder(PositionType ptype, NewContract contract, Action action, Integer qty, Double base_price, Double profit_price, Double cut_loss_price, String ref) {
		IBBracketOrder br = new IBBracketOrder(ptype, contract, ref, action, base_price, qty, profit_price, cut_loss_price);
		br.place(ctrl);

		IBOrder main = br.getOrder(WHICH_ORDER.MAIN);
		storeIBOrder(main);
		IBOrder profit = br.getOrder(WHICH_ORDER.TAKE_PROFIT);
		storeIBOrder(profit);
		IBOrder cutloss = br.getOrder(WHICH_ORDER.CUT_LOSS);
		storeIBOrder(cutloss);

		Integer oid = generateBracketOrderId();
		bracket_orders.put(oid, br);

		// keep track of all order id's in this "package" ?
		return oid;
	}

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	public Integer modifyBracketOrder(Integer oid, Integer qty, Double base_price, Double profit_price, Double cut_loss_price) {
		IBBracketOrder br = bracket_orders.get(oid);
		if (br != null) {
			br.modifyAndPlaceChanges(ctrl, base_price, profit_price, cut_loss_price); // FIX!!!
			return oid;
		} else {
			logErr("ERROR: Bracket order " + bracket_order_id + " not found!");
			return -1;
		}
	}

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	public void cancelBracketOrder(Integer bracket_order_id) {
		IBBracketOrder br = bracket_orders.get(bracket_order_id);
		if (br != null) {
			br.cancelOrder(ctrl);
		} else {
			logErr("ERROR: Bracket order " + bracket_order_id + " not found!");
		}
	}

	public void cancelOrder(int orderId) {
		IBOrder order = orders.get(orderId);
		if (order == null) {
			logErr("Order ID " + orderId + " not found in OrderManager!");
		} else {
			order.cancelOrder(ctrl);
		}
	}

	public void cancelAllOrders() {
		ctrl.cancelAllOrders();
	}

	private static Integer bracket_order_id = 1;

	private Integer generateBracketOrderId() {
		return bracket_order_id++;
	}

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	public void listOrders() {
		logMsg("----------- ORDERLIST (FROM LIVE ORDERS) -------------------------------");
		for (IBOrder od : orders.values()) {
			// if (od.getStatus() != OrderStatus.Cancelled && od.getStatus() != OrderStatus.Filled) {
			logMsg("ID:[" + od.orderId() + "]" + " PermId:" + od.permId() + " Type:" + od.orderType().name() + " Status:" + od.getStatus().name() + " Symbol:" + od.getContract().symbol() + " Action:" + od.action() + " LmtPrice:" + asCurr(od.lmtPrice()) + " AuxPrice:"
					+ asCurr(od.auxPrice()) + " Qty:" + od.totalQuantity());
			// }
		}
		logMsg("----------- END -------------------------------------------------------");
	}

	private String asCurr(Double d) {
		if (d > 100000000.0)
			return "[TOOBIG]";
		if (d < -100000000.0)
			return "[TOOSMALL]";
		return String.format("%1.2f", d);
	}

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	public List<IBOrder> listExecutedOrders() {

		List<IBOrder> o = new ArrayList<IBOrder>();
		for (IBOrder od : orders.values()) {
			if (od.getStatus() != OrderStatus.Filled) {
				o.add(od);
			}
		}

		return o;
	}

	public void listExecutions() {
		IBExecution exec;
		for (String key : executions.keySet()) {
			logMsg("symbol=" + executions.get(key).getContract().symbol());
		}
	}

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	private void requestExecutions() {
		ctrl.reqExecutions(new ITradeReportHandler() {
			@Override
			public void tradeReport(String tradeKey, NewContract contract, Execution execution) {
				logMsg("------ TRADE --------");
				IBContract ib = new IBContract(ctrl, contract);
				IBExecution e = new IBExecution(tradeKey, ib, execution);
				executions.put(tradeKey, e);

				IBOrder order = orders.get(execution.m_orderId);
				if (order == null) {
					logErr("EXECUTION: Order " + execution.m_orderId + " not found!");
				}
				for (ITradingEventHandler eh : trading_event_handlers) {
					eh.tradeAdded(order, e);
				}
			}

			@Override
			public void tradeReportEnd() {
				listExecutions();
			}

			@Override
			public void commissionReport(String tradeKey, CommissionReport commissionReport) {
				IBExecution e = executions.get(tradeKey);
				if (e != null) {
					e.addCommissionReport(commissionReport);
				}
			}
		});
	}

	private void requestPositions() {
		ctrl.reqPositions(new IPositionHandler() {
			@Override
			public void positionEnd() {
				logMsg("------ POSITION UPDATE END ------");
			}

			@Override
			public void position(String account, NewContract contract, int position, double avgCost) {
				logMsg("------ POSITION UPDATE ------");
			}
		});
	}

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	private void requestLiveOrders() {

		ctrl.reqLiveOrders(new ILiveOrderHandler() {

			/*
			 * Open Order : order has been added
			 */
			@Override
			public void openOrder(NewContract contract, NewOrder order, NewOrderState orderState) {

				logMsg("LIVE: OPENORDER " + contract.symbol() + " Acct:" + order.account() + " Id:" + order.orderId() + " TotalQty:" + order.totalQuantity() + " state:" + orderState.status());

				IBOrder o = makeIBOrder(order.account(), contract, order);

				o = storeIBOrder(o);

				NewOrderState old = o.getOrderState();
				if (old != null) {

					int changes = 0;

					if (old.commission() != orderState.commission()) {
						logMsg("openOrder - Commission changed");
						changes++;
					}

					if (old.minCommission() != orderState.minCommission()) {
						logMsg("openOrder - Min Commission changed");
						changes++;
					}
					if (old.maxCommission() != orderState.maxCommission()) {
						logMsg("openOrder - Max Commission changed");
						changes++;
					}

					if (old.status() != orderState.status()) {
						logMsg("openOrder - Status changed");
						changes++;
					}

					if (!equStr(old.commissionCurrency(), orderState.commissionCurrency())) {
						logMsg("openOrder - Commission currency changed");
						changes++;
					}

					if (!equStr(old.equityWithLoan(), orderState.equityWithLoan())) {
						logMsg("openOrder - equityWithLoan changed");
						changes++;
					}
					if (!equStr(old.initMargin(), orderState.initMargin())) {
						logMsg("openOrder - initWithMargin changed");
						changes++;
					}
					if (!equStr(old.maintMargin(), orderState.maintMargin())) {
						logMsg("openOrder - maintMargin changed");
						changes++;
					}
					if (!equStr(old.warningText(), orderState.warningText())) {
						logMsg("openOrder - warningText changed");
						changes++;
					}

					if (changes == 0) {
						logMsg("openOrder - OrderState has NO changes");
					} else {
						logMsg("openOrder - OrderStates has " + changes + " changes");
					}

				} else {
					// all state fields are updated!
					logMsg("openOrder - first update so all fields are updated!");
				}

				o.setOrderState(orderState);

				/*
				 * check what happened!
				 * A) Order was submitted tomarket (status = Submitted)
				 * B) Order was partly traded
				 * C) Order was fully trades
				 * D) Order was cancelled
				 */

				switch (orderState.status()) {

					case PreSubmitted:
					break;

					case Submitted:
						for (ITradingEventHandler eh : trading_event_handlers) {
							eh.orderAdded(o);
						}
					break;

				}

			}

			@Override
			public void openOrderEnd() {
				logMsg("--- LIVE - END OF ORDERS ---");
				listOrders();
			}

			/*
			 * handle : error on placing order!
			 */

			@Override
			public void handle(int orderId, int errorCode, String errorMsg) {

				IBOrder ibOrder = orders.get(orderId);
				if (ibOrder != null) {
					logMsg("LIVE: errorHandler() : orderId:" + orderId + " error:" + errorCode + " msg:" + errorMsg);
					for (ITradingEventHandler eh : trading_event_handlers) {
						eh.orderCancelled(ibOrder);
					}
				} else {
					//
				}

			}

			/*
			 * OrderStatus : order status changed
			 */
			@Override
			public void orderStatus(int orderId, OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
				IBOrder ibOrder = orders.get(orderId);
				if (ibOrder == null) {
					logMsg("LIVE: orderStatus: order " + orderId + " not found! SERIOUS ERROR!!!");
				} else {
					logMsg("LIVE: orderStatus: order " + orderId + " status updated to:" + status.name());
					ibOrder.setStatus(status);
				}
			}

		});
	}

	private Boolean equStr(String a, String b) {
		if (a == null && b == null)
			return true;
		if (a == null)
			return false;
		if (b == null)
			return false;
		return a.equals(b);
	}

	/*
	 * ------------------------------------------------------------
	 * ------------------------------------------------------------
	 */
	private IBOrder makeIBOrder(String account, NewContract contract, NewOrder order) {
		IBOrder od = new IBOrder(contract, order.orderId(), account, order.action(), order.orderType(), order.lmtPrice(), order.totalQuantity(), order.tif(), null);
		od.permId(order.permId());
		return od;
	}

	private IBOrder storeIBOrder(IBOrder ibOrder) {

		IBOrder o = orders.get(ibOrder.orderId());
		if (o != null) {
			logMsg("LIVE: Order " + ibOrder.orderId() + " already existed (OK)");
			orders.put(ibOrder.orderId(), ibOrder);
			return o;
		} else {
			logMsg("LIVE: openOrder() Storing order id " + ibOrder.orderId());
			orders.put(ibOrder.orderId(), ibOrder);
			return ibOrder;
		}
	}

}
