package examples;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import examples.IBBracketOrder.PositionType;
import ib.NewContract;
import ib.NewOrder;
import ib.OrderType;
import ib.Types.Action;
import ib.Types.SecType;
import ib.Types.TimeInForce;

/*
 * A more advanced version of TestIBController
 *
 *	Will connect to Alpha System (prod normallly) 
 * 	Will connect to IB (local paper trading normally)
 * 
 * 
 */
public class TestIBController2 implements IIBControllerEventHandler {

	IBController						ctrl;
	OrderManager						om;

	private final NewContract		m_contract		= new NewContract();
	private final NewOrder			m_order			= new NewOrder();
	private Map<String, IBContract>	ibcontracts		= new HashMap<String, IBContract>();
	
	private String					version			= "TestIController2 version 1.02";
	private IBContract 				contract;
	
//	CONFIG STUFF
//	private String 					alpha_hostname	= "alphatrading.dnsalias.com";
//	private String 					alpha_port		= "38888";
	private String 					alpha_hostname	= "192.168.0.198";
	private String 					alpha_port		= "8888";
	
	private String 					ib_hostname		= "192.168.0.198";
	private Integer					ib_port			= 6661;
	private Integer 					ib_client_id		= 123;
	private String					paper_account	= "DU192885";
	
//	private String					ctc_symbol		= "SAND";
//	private String					ctc_exchange		= "SFB";
//	private String					ctc_currency		= "SEK";
//	private SecType					ctc_type			= SecType.STK;
	private String					ctc_symbol		= "AAPL";
	private String					ctc_exchange		= "SMART";
	private String					ctc_currency		= "USD";
	private SecType					ctc_type			= SecType.STK;
	
	private Integer bracket_order_id;
	
	private IBOrder single_order;

	public static void main(String[] args) {
		new TestIBController2();
	}

	/*	-------------------------------------------------------------------------------------------------------
	 * 
	 * 
	 * 
	 * 
	 * 
	 *	------------------------------------------------------------------------------------------------------- 
	 */

	public TestIBController2() {

		ctrl = new IBController(this);
		System.out.println("WARNING: This app places orders! Make sure you run against a testsystem!");
		ctrl.connect(ib_hostname, ib_port, /* clientid: */ib_client_id);
		
		contract = new IBContract(ctrl, ctc_type, ctc_symbol, ctc_exchange, ctc_currency);
		contract.reqPrices(); // IBContract is now updated with market prices

		om = new OrderManager(ctrl, paper_account);
		om.start();
		
		om.subscribeToTradingEvents(new ITradingEventHandler() {
			@Override
			public void tradeAdded(IBOrder order, IBExecution execution) {
				logMsg(">>>>>>>> tradeAdded() application  callback");
			}
			
			@Override
			public void orderAdded(IBOrder order) {
				System.out.println(">>>>>>>> orderAdded() application  callback");
			}
			
			@Override
			public void orderModifed(IBOrder order) {
				logMsg(">>>>>>>> orderModified() application  callback");
			}
			
			@Override
			public void orderCancelled(IBOrder order) {
				logMsg(">>>>>>>> orderCancelled() application  callback");
			}
		});

		menuForever();
	}

	private void logMsg(String str) {
		System.out.println("TestIBController2 " + " " + str);
	}
	
	private void logErr(String str) {
		System.out.println("TestIBController2 " + " " + str);
	}
	
	/*
	 * 
	 * 
	 */
	private void menuForever() {
		System.out.println("    ---menu---");
		System.out.println("b : send bracket order");
		System.out.println("c : modify bracket order");
		System.out.println("o : list all orders");
		System.out.println("q : quit");
		
		Scanner s = new Scanner(System.in).useDelimiter("\\s*");
		while (!s.hasNext("q")) {
			Character ch = s.next().charAt(0);
			System.out.print("[" + ch + "] ");
			switch (Character.toUpperCase(ch)) {
				default:
				case ' ':
					om.listOrders();
				break;
				case 'B':
					// LONG POS
					bracket_order_id = om.sendBracketOrder(
						PositionType.LONG,
						contract, 
						Action.BUY,
						100,
						contract.getAsk(),
						contract.getAsk()+0.16,
						contract.getBid()-0.16, 
						"TEST");
				break;
				
				case 'S':
					// SHORT POS
					bracket_order_id = om.sendBracketOrder(
						PositionType.SHORT,
						contract, 
						Action.SELL,
						100,
						contract.getBid(),			// Base 
						contract.getBid()-0.16,		// Profit
						contract.getAsk()+0.16, 		// CutLoss
						"TEST");
				break;
				
				case 'C':
					om.modifyBracketOrder(
						bracket_order_id,
						100,
						contract.getAsk(),
						contract.getAsk()+0.32,
						contract.getBid()-0.32);
				break;

				case 'D':
					om.cancelBracketOrder(bracket_order_id);
				break;
				
				case 'E':
					om.cancelOrder(single_order.orderId());
				break;
				
				case 'X':
					om.cancelAllOrders();
				break;
				
				case '1':
					logMsg("Placing order with qty=10 and price=" + (contract.getAsk()-1.0));
					single_order = new IBOrder(
						contract,
						0, // 0 = new order!
						paper_account, 
						Action.BUY, 
						OrderType.LMT, 
						contract.getAsk() - 1.0, 
						10, 
						TimeInForce.DAY,
						null);
					single_order.submit(ctrl, true);
				break;
				
				case '2':
					logMsg("Price was:" + single_order.lmtPrice());
					single_order.lmtPrice(single_order.lmtPrice() - 0.10);
					single_order.submit(ctrl, true);
				break;
				
				case '3':
					logMsg("Price was:" + single_order.lmtPrice());
					single_order.lmtPrice(single_order.lmtPrice() + 0.10);
					single_order.submit(ctrl, true);
				break;
				
//				case '9':
//					single_order = new IBOrder(
//						contract,
//						0, // 0 = new order!
//						paper_account, 
//						Action.SELL, 
//						OrderType.MKT, 
//						0.0, 
//						10, 
//						TimeInForce.DAY,
//						null);
//					single_order.lmtPrice(0.0);
//					single_order.auxPrice(0.0);
//					
//					String t = DateTools.getTimeAsStr(System.currentTimeMillis() + 30000, false);
//					single_order.goodAfterTime(t);
//					single_order.submit(ctrl, true);
//				break;
				
				case 'o':
				case 'O':
					om.listOrders();
				break;
				
				
				case 'm':
				case 'M':
					
				break;
					
				case 'Q':
				case 'q':
					System.exit(444);
				break;
			}
			System.out.println("Command:");
		}
		;
	}

	@Override
	public void connectedToTws() {
		logMsg("Connected to TWS!");
	}

	@Override
	public void disconnectedFromTws() {
		logMsg("Disconnected to TWS!");
	}

	@Override
	public void infoFromTws(int id, int code, String msg) {
		logMsg("Info from TWS:" + id + " " + code + " " + msg);
	}

}
