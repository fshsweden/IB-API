package examples;

import ib.ApiController.ITopMktDataHandler;
import ib.NewContract;
import ib.NewContractDetails;
import ib.NewTickType;
import ib.Types.MktDataType;
import ib.Types.SecType;

/**
 * Contracts
 * 
 *
 */

public class IBContract extends NewContract {
	
	private int bid_size,ask_size;
	private double bid,ask;
	private IBController ctrl;
	private NewContractDetails ncd = null;
	
	public IBContract(IBController ctrl, NewContract contract) {
	
		secType(contract.secType());
		symbol(contract.symbol());
		exchange(contract.exchange());
		currency(contract.currency());
	}

	public IBContract(IBController ctrl, NewContract contract, NewContractDetails ncd) {
		
		secType(contract.secType());
		symbol(contract.symbol());
		exchange(contract.exchange());
		currency(contract.currency());
		
		this.ncd = ncd;
	}
	
	public IBContract(IBController ctrl, SecType secType, String symbol, String exch, String curr) {
		
		this.ctrl = ctrl;
		
		secType(secType);
		symbol(symbol);
		exchange(exch);
		currency(curr);
	}
	
	public NewContractDetails getContractDetails() {
		return ncd;
	}
	
	public String getIdString() {
		switch (secType()) {
			default:
			case STK:
				return symbol();
			case FUT:
				return symbol() + "-" + expiry();
			case OPT:
				return symbol() + "-" + expiry() + "-" + strike();
		}
	}
	
	
	public void updated(NewContract contract) {
//		logMsg("--------- CONTRACT UPDATED ---------");
//		logMsg(contract.toString());
//		logMsg("------- END OF CONTRACT      -------");
	}
	
	public int getBidSize() {
		return bid_size;
	}

	public void setBidSize(int bid_size) {
		this.bid_size = bid_size;
	}

	public int getAskSize() {
		return ask_size;
	}

	public void setAskSize(int ask_size) {
		this.ask_size = ask_size;
	}

	public double getBid() {
		return bid;
	}

	public void setBid(double bid) {
		this.bid = bid;
	}

	public double getAsk() {
		return ask;
	}

	public void setAsk(double ask) {
		this.ask = ask;
	}
	
	public String getMarket() {
		return "" + getBidSize() + "/" + getBid() + " --- " + getAsk() + "/" + getAskSize();
	}
	
	
	public void reqPrices() {
		int request_id = ctrl.reqTopMktData(this, "", false, new ITopMktDataHandler() {

			@Override
			public void tickString(NewTickType tickType, String value) {
				if (!(tickType.name().equals("LAST_TIMESTAMP"))) {
					System.out.println(symbol() + " " + tickType.name() + " " + value);
				}
			}

			@Override
			public void tickSnapshotEnd() {
				System.out.println("tickSnapshotEnd");
			}

			@Override
			public void tickSize(NewTickType tickType, int size) {
				//System.out.println(symbol() + ": Size:" + size);
				switch (tickType) {
					case BID_SIZE:
						setBidSize(size);
					break;
					case ASK_SIZE:
						setAskSize(size);
					break;
				}
				// System.out.println(getMarket());
			}

			@Override
			public void tickPrice(NewTickType tickType, double price, int canAutoExecute) {
				//System.out.println(symbol() + ": Price:" + price);
				switch (tickType) {
					case BID:
						setBid(price);
					break;
					case ASK:
						setAsk(price);
					break;
				}
				// System.out.println(getMarket());
			}

			@Override
			public void marketDataType(MktDataType marketDataType) {
				System.out.println(symbol() + " marketDataType");
			}
		});

		System.out.println("Request TopMkt was request id " + request_id);
	}
}
