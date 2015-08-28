package examples;

import java.util.HashMap;
import java.util.Map;

import ib.ApiController.IAccountHandler;
import ib.Position;

public class IBAccount {
	
	private IBController ctrl;
	
	public IBAccount(IBController ctrl) {
		this.ctrl = ctrl;
	}
	
	private Map<String, Position> positions = new HashMap<String,Position>();
	private Map<String,String> accValues = new HashMap<String,String>();
	private String accTime;
	
	private void requestAccountUpdates(String account) {
		/**
		 * 
		 */
		ctrl.reqAccountUpdates(true, account, new IAccountHandler() {
			@Override
			public void updatePortfolio(Position position) {
				IBContract c = new IBContract(ctrl, position.contract());
				positions.put(c.getIdString(),position);
			}

			@Override
			public void accountValue(String account, String key, String value, String currency) {
				accValues.put(account+"-"+key+"-"+currency, value);
			}

			@Override
			public void accountTime(String timeStamp) {
				accTime = timeStamp;
			}

			@Override
			public void accountDownloadEnd(String account) {
				// System.out.println("accountDownloadEnd(String account)");
			}
		});
	}
	
}
