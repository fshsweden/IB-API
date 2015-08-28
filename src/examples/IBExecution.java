package examples;

import ib.CommissionReport;
import ib.Execution;

public class IBExecution extends Execution {
	
	private String tradeKey;
	private IBContract contract;
	private CommissionReport commission_report = null;
	
	public IBExecution(String tradeKey, IBContract contract, Execution execution) {
		super();
		
	    m_orderId		= execution.m_orderId;
	    m_clientId		= execution.m_clientId;
	    m_execId			= execution.m_execId;
	    m_time			= execution.m_time;
	    m_acctNumber		= execution.m_acctNumber;
	    m_exchange		= execution.m_exchange;
	    m_side			= execution.m_side;
	    m_shares			= execution.m_shares;
	    m_price			= execution.m_price;
	    m_permId			= execution.m_permId;
	    m_liquidation	= execution.m_liquidation;
	    m_cumQty			= execution.m_cumQty;
	    m_avgPrice		= execution.m_avgPrice;
	    m_orderRef		= execution.m_orderRef;
	    m_evRule			= execution.m_evRule;
	    m_evMultiplier	= execution.m_evMultiplier;
		
		this.contract 	= contract;
		this.tradeKey	= tradeKey;
	}
	
	public void addCommissionReport(CommissionReport r)
	{
		commission_report = r;
	}
	
	public CommissionReport getCommissionReport() {
		return commission_report;
	}
	
	public String getTradeKey() {
		return tradeKey;
	}
	
	public IBContract getContract() {
		return contract;
	}
	

}
