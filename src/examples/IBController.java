package examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ib.AccountSummaryTag;
import ib.ApiConnection;
import ib.ApiController;
import ib.ApiController.IAccountHandler;
import ib.ApiController.IAccountSummaryHandler;
import ib.ApiController.IConnectionHandler;
import ib.ApiController.IContractDetailsHandler;
import ib.ApiController.IHistoricalDataHandler;
import ib.ApiController.ILiveOrderHandler;
import ib.ApiController.IOrderHandler;
import ib.ApiController.IPositionHandler;
import ib.ApiController.ITopMktDataHandler;
import ib.ApiController.ITradeReportHandler;
import ib.Bar;
import ib.CommissionReport;
import ib.Execution;
import ib.ExecutionFilter;
import ib.NewContract;
import ib.NewContractDetails;
import ib.NewOrder;
import ib.Types.BarSize;
import ib.Types.DurationUnit;
import ib.Types.WhatToShow;

/**
 * IBMarketController extends the IB-supplied ApiController
 * 
 * @author Peter Andersson
 *
 */
public class IBController implements IConnectionHandler {

	private ApiController				api_controller		= null;
	private IIBControllerEventHandler	handler				= null;
	private boolean						connected_to_tws	= false;

	private AtomicInteger				next_order_id		= new AtomicInteger(-1);	/* we keep track of the order id's! */

	public int reqContractDetails(NewContract contract, IContractDetailsHandler handler) {
		api_controller.reqContractDetails(contract, handler);
		return api_controller.getNextReqId() - 1;
	}

	public List<NewContractDetails> reqContractDetailsSynch(NewContract contract) {
		List<NewContractDetails> result = new ArrayList<NewContractDetails>();
		CountDownLatch c = new CountDownLatch(1);
		api_controller.reqContractDetails(contract, new IContractDetailsHandler() {
			@Override
			public void contractDetails(ArrayList<NewContractDetails> list) {
				result.addAll(list);
				c.countDown();
			}
		});
		try {
			c.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return result;
	}

	public void reqPositions(IPositionHandler handler) {
		api_controller.reqPositions(handler);
	}

	public class PositionDetails {
		private String		account;
		private NewContract	contract;
		private int			position;
		private double		avgCost;

		public PositionDetails(String account, NewContract contract, int position, double avgCost) {
			super();
			this.account = account;
			this.contract = contract;
			this.position = position;
			this.avgCost = avgCost;
		}

		public String getAccount() {
			return account;
		}

		public void setAccount(String account) {
			this.account = account;
		}

		public NewContract getContract() {
			return contract;
		}

		public void setContract(NewContract contract) {
			this.contract = contract;
		}

		public int getPosition() {
			return position;
		}

		public void setPosition(int position) {
			this.position = position;
		}

		public double getAvgCost() {
			return avgCost;
		}

		public void setAvgCost(double avgCost) {
			this.avgCost = avgCost;
		}
	}

	public List<PositionDetails> reqPositionsSynch() {
		List<PositionDetails> result = null;
		CountDownLatch c = new CountDownLatch(1);
		api_controller.reqPositions(new IPositionHandler() {
			@Override
			public void position(String account, NewContract contract, int position, double avgCost) {
				PositionDetails pd = new PositionDetails(account, contract, position, avgCost);
				result.add(pd);
			}

			@Override
			public void positionEnd() {
				c.countDown();
			}
		});
		try {
			c.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return result;
	}

	public IBController(IIBControllerEventHandler h) {
		api_controller = new ApiController(this);
		this.handler = h;
		version();
	}

	public IBController(IConnectionHandler h) {
		api_controller = new ApiController(h);
		version();
	}

	private void version() {
		System.out.println("IBController compiled with:");
		System.out.println("    This Client API's version:" + ApiConnection.CLIENT_VERSION);
		System.out.println("    Minimal accepted Server version:" + ApiConnection.SERVER_VERSION);
	}

	// /////////////////////////////////////////////////////////////////////////////
	// /////////////////////////////////////////////////////////////////////////////
	// / M E T H O D S
	// /////////////////////////////////////////////////////////////////////////////
	// /////////////////////////////////////////////////////////////////////////////

	CountDownLatch	latch	= new CountDownLatch(1);

	public void connect(String host, int port, int clientId) {

		api_controller.connect(host, port, clientId);

		try {
			latch.await(60, TimeUnit.SECONDS);

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void disconnect() {
		api_controller.disconnect();
	}

	public void reqExecutions(ITradeReportHandler handler) {
		api_controller.reqExecutions(new ExecutionFilter(), handler);
	}

	public void reqExecutions(ExecutionFilter filter, ITradeReportHandler handler) {
		api_controller.reqExecutions(filter, handler);
	}

	public class ExecutionReport {
		private Execution			execution;
		private CommissionReport	commissionreport;
		private NewContract			contract;

		public ExecutionReport(Execution execution, CommissionReport commissionreport, NewContract contract) {
			super();
			this.execution = execution;
			this.commissionreport = commissionreport;
			this.contract = contract;
		}

		public Execution getExection() {
			return execution;
		}

		public void setExecution(Execution execution) {
			this.execution = execution;
		}

		public CommissionReport getCommissionreport() {
			return commissionreport;
		}

		public void setCommissionreport(CommissionReport commissionreport) {
			this.commissionreport = commissionreport;
		}

		public NewContract getContract() {
			return contract;
		}

		public void setContract(NewContract contract) {
			this.contract = contract;
		}
	}

	public Map<String, ExecutionReport> reqExecutionsSynch() {

		Map<String, ExecutionReport> exrep = new HashMap<String, ExecutionReport>();
		AtomicBoolean end_recvd = new AtomicBoolean();

		end_recvd.set(false);

		api_controller.reqExecutions(new ExecutionFilter(), new ITradeReportHandler() {
			@Override
			public void tradeReportEnd() {
				end_recvd.set(true);
			}

			@Override
			public void tradeReport(String tradeKey, NewContract contract, Execution execution) {
				if (exrep.get(tradeKey) == null) {
					exrep.put(tradeKey, new ExecutionReport(execution, null, contract));
				} else {
					exrep.get(tradeKey).setContract(contract);
					exrep.get(tradeKey).setExecution(execution);
				}
			}

			@Override
			public void commissionReport(String tradeKey, CommissionReport commissionReport) {
				if (exrep.get(tradeKey) == null) {
					exrep.put(tradeKey, new ExecutionReport(null, commissionReport, null));
				} else {
					exrep.get(tradeKey).setCommissionreport(commissionReport);
				}
			}
		});

		/*
		 * Make this request synchronous!
		 */
		while (!end_recvd.get()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}

		return exrep;
	}

	public int reqTopMktData(NewContract contract, String genericTickList, boolean snapshot, ITopMktDataHandler handler) {
		api_controller.reqTopMktData(contract, genericTickList, snapshot, handler);
		return api_controller.getNextReqId() - 1; // HACK!
	}

	public void reqLiveOrders(ILiveOrderHandler handler) {
		api_controller.reqLiveOrders(handler);
		// api_controller.takeTwsOrders(handler);
		// api_controller.takeFutureTwsOrders(handler);
	}

	
	

	public int getNextOrderId() {
		return next_order_id.getAndIncrement();
	}

	/*
	 * order.orderId() == 0 means New order, else Modify order
	 */
	public int placeOrModifyOrder(NewContract contract, NewOrder order, IOrderHandler handler) {
		assert(contract != null);
		assert(order != null);
		int order_id = 0;
		if (order.orderId() > 0) {
			order_id = order.orderId();
		}
		else {
			order_id = api_controller.allocateOrderId();
			order.orderId(order_id);
		}
		api_controller.placeOrModifyOrder(contract, order, handler);
		return order_id;
	}
	
	public void removeOrderHandler(IBOrder order, IOrderHandler handler) {
		api_controller.removeOrderHandler(order.orderId(), handler);
	}

	public void cancelOrder(int orderId) {
		api_controller.cancelOrder(orderId);
	}

	public void cancelAllOrders() {
		api_controller.cancelAllOrders();
	}

	public void reqAccountSummary(IAccountSummaryHandler handler) {
		api_controller.reqAccountSummary("All", AccountSummaryTag.values(), handler);
	}

	public void reqAccountUpdates(boolean subscribe, String account, IAccountHandler handler) {
		api_controller.reqAccountUpdates(subscribe, account, handler);
	}

	public List<Bar> reqHistoricalDataSynch(NewContract contract, String endDateTime, int duration, DurationUnit durationUnit, BarSize barSize, WhatToShow whatToShow, boolean rthOnly) {
		List<Bar> bars = new ArrayList<Bar>();

		CountDownLatch c = new CountDownLatch(1);

		api_controller.reqHistoricalData(contract, endDateTime, duration, durationUnit, barSize, whatToShow, rthOnly, new IHistoricalDataHandler() {

			@Override
			public void historicalDataEnd() {
				// System.out.println("--- Done ---");
				c.countDown();
			}

			@Override
			public void historicalData(Bar bar, boolean hasGaps) {
				bars.add(bar);
			}
		});

		try {
			c.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return bars;
	}

	public List<Bar> reqHistoricalData(NewContract contract, String endDateTime, int duration, DurationUnit durationUnit, BarSize barSize, WhatToShow whatToShow, boolean rthOnly, IHistoricalDataHandler handler) {
		List<Bar> bars = new ArrayList<Bar>();

		CountDownLatch c = new CountDownLatch(1);

		api_controller.reqHistoricalData(contract, endDateTime, duration, durationUnit, barSize, whatToShow, rthOnly, handler);

		try {
			c.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return bars;
	}

	// /////////////////////////////////////////////////////////////////////////////
	// /////////////////////////////////////////////////////////////////////////////
	// / E V E N T S
	// /////////////////////////////////////////////////////////////////////////////
	// /////////////////////////////////////////////////////////////////////////////

	@Override
	public void connected(final int nextValidOrderId) {

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		connected_to_tws = true;
		latch.countDown();
		/*
		 * ApiController now has set m_reqid and m_orderid !
		 */

		next_order_id = new AtomicInteger(nextValidOrderId);

		if (handler != null)
			handler.connectedToTws();
	}

	@Override
	public void disconnected() {
		if (handler != null)
			handler.disconnectedFromTws();
		;
	}

	@Override
	public void accountList(ArrayList<String> list) {
		// TODO Auto-generated method stub
	}

	@Override
	public void error(Exception e) {
		if (handler != null)
			handler.infoFromTws(-1, -1, e.getLocalizedMessage());
	}

	@Override
	public void message(int id, int errorCode, String errorMsg) {
		if (handler != null)
			handler.infoFromTws(id, errorCode, errorMsg);
	}

	@Override
	public void show(String string) {
		if (handler != null)
			handler.infoFromTws(-1, -1, string);
	}

}
