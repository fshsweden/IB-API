package examples;

import java.util.ArrayList;
import java.util.List;

import ib.ApiController.IContractDetailsHandler;
import ib.NewContractDetails;
import ib.Types;

public class IBContractSearch {

	/*
	 * TODO: Add more searches for FUTURES etc
	 */
	public static List<IBContract> search(IBController ctrl,  Types.SecType type, String symbol, String curr, String exch) {

		List<IBContract> results = new ArrayList<IBContract>();
		IBContract search_contract = new IBContract(ctrl, type, symbol,curr,exch);

		ctrl.reqContractDetails(search_contract, new IContractDetailsHandler() {
			@Override
			public void contractDetails(ArrayList<NewContractDetails> list) {
				for (NewContractDetails ncd : list) {
					
					IBContract c = new IBContract(ctrl, ncd.contract(), ncd);  
					results.add(c);
				}
			}
		});

		return results;
	}

}
