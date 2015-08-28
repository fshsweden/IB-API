package examples;

import examples.IBOrder.IBOrderStatus;
import ib.NewContract;
import ib.OrderType;
import ib.Types.Action;
import ib.Types.TimeInForce;

/*	-------------------------------------------------------------------------
 *	 
 *	 
 *	 
 *	 
 *	 
 *	------------------------------------------------------------------------- 
 */
public class IBBracketOrder {

	private IBOrder		main_order;
	private IBOrder		take_profit_order;
	private IBOrder		cut_loss_order;

	private NewContract	contract;

	public enum PositionType {
		LONG, SHORT
	};

	private PositionType	position_type;

	private enum State {
		INIT, INMARKET, PARTIALLY_EXECUTED, WHOLLY_EXECUTED, PROFIT_TAKEN, CUTLOSS_TAKEN
	};

	private State			state;
	private final Boolean	TRANSMIT			= true;
	private final Boolean	DONT_TRANSMIT		= false;

	/* --------------------------------------------------------------------------------------------------------------------------------------
	 * 
	 * --------------------------------------------------------------------------------------------------------------------------------------
	 */
	public IBBracketOrder(PositionType ptype, NewContract c, String account, Action action, Double price, int volume, Double take_profit_price, Double cut_loss_price) {
		assert (c != null);
		assert (price != 0.0);
		assert (volume != 0);

		this.position_type = ptype;
		this.contract = c;

		Action reverse_action = action == Action.BUY ? Action.SELL : Action.BUY;

		/* BASE ORDER */
		main_order = new IBOrder(c, 0 /* 0 = new order */, account, action, OrderType.LMT, price, volume, TimeInForce.DAY, null, DONT_TRANSMIT);

		// BUG: main_order.orderId() not set yet!! */
		
		/* TAKE-PROFIT ORDER */
		take_profit_order = new IBOrder(c, 0 /* 0 = new order */, account, reverse_action, OrderType.LMT, take_profit_price, volume, TimeInForce.DAY, main_order, DONT_TRANSMIT);

		/* CUT-LOSS ORDER */
		cut_loss_order = new IBOrder(c, 0 /* 0 = new order */, account, reverse_action, OrderType.STP, cut_loss_price, volume, TimeInForce.DAY, main_order, TRANSMIT);
	}

	public enum WHICH_ORDER {
		MAIN, TAKE_PROFIT, CUT_LOSS
	};

	public IBOrder getOrder(WHICH_ORDER which_order) {
		switch (which_order) {
			case MAIN:
			default:
				return main_order;
			case CUT_LOSS:
				return cut_loss_order;
			case TAKE_PROFIT:
				return take_profit_order;
		}
	}

	public int getOrderId(WHICH_ORDER which_order) {
		switch (which_order) {
			case MAIN:
			default:
				return main_order.orderId();
			case CUT_LOSS:
				return cut_loss_order.orderId();
			case TAKE_PROFIT:
				return take_profit_order.orderId();
		}
	}

	/*
	 * --------------------------------------------------------
	 * --------------------------------------------------------
	 */
	public void modifyAndPlaceChanges(IBController ctrl, Double base_price, Double take_profit_price, Double cut_loss_price) {
		/*
		 * Orders that are fully traded (Filled) or not at all in market (None or Cancelled) should not be touched
		 */
		if ((main_order.getOrderStatus() == IBOrderStatus.InMarket || main_order.getOrderStatus() == IBOrderStatus.InMarketPartlyFilled) && base_price != main_order.lmtPrice()) {
			modifyOrder(ctrl, main_order, base_price);
		}

		if ((take_profit_order.getOrderStatus() == IBOrderStatus.InMarket || take_profit_order.getOrderStatus() == IBOrderStatus.InMarketPartlyFilled) && take_profit_price != take_profit_order.lmtPrice()) {
			modifyOrder(ctrl, take_profit_order, take_profit_price);
		}

		if ((cut_loss_order.getOrderStatus() == IBOrderStatus.InMarket || cut_loss_order.getOrderStatus() == IBOrderStatus.InMarketPartlyFilled) && cut_loss_price != cut_loss_order.lmtPrice()) {
			modifyOrder(ctrl, cut_loss_order, cut_loss_price);
		}
	}

	/*
	 * --------------------------------------------------------
	 * --------------------------------------------------------
	 */
	public void cancelOrder(IBController ctrl)
	{
		/*
		 * Orders that are fully traded (Filled) or not at all in market (None or Cancelled) should not be touched  
		 */
		if (main_order.getOrderStatus() == IBOrderStatus.InMarket || main_order.getOrderStatus() == IBOrderStatus.InMarketPartlyFilled	) {
			main_order.cancelOrder(ctrl);
		}
		
		if (take_profit_order.getOrderStatus() == IBOrderStatus.InMarket || take_profit_order.getOrderStatus() == IBOrderStatus.InMarketPartlyFilled) {
			take_profit_order.cancelOrder(ctrl);
		}
		
		if (cut_loss_order.getOrderStatus() == IBOrderStatus.InMarket || cut_loss_order.getOrderStatus() == IBOrderStatus.InMarketPartlyFilled) {
			cut_loss_order.cancelOrder(ctrl);
		}
	}

	private void modifyOrder(IBController ctrl, IBOrder order, Double price) {
		// take_profit_order.lmtPrice(take_profit_price);
		switch (order.orderType()) {
			case STP:
				order.auxPrice(price);
				order.transmit(true);
				order.submit(ctrl, false);
			break;
			case LMT:
				order.lmtPrice(price);
				order.transmit(true);
				order.submit(ctrl, false);
			break;
			// we dont support any more order types!
			case BOX_TOP:
			break;
			case FIX_PEGGED:
			break;
			case LIT:
			break;
			case LMT_PLUS_MKT:
			break;
			case LOC:
			break;
			case MIT:
			break;
			case MKT:
			break;
			case MKT_PRT:
			break;
			case MOC:
			break;
			case MTL:
			break;
			case None:
			break;
			case PASSV_REL:
			break;
			case PEG_BENCH:
			break;
			case PEG_MID:
			break;
			case PEG_MKT:
			break;
			case PEG_PRIM:
			break;
			case PEG_STK:
			break;
			case REL:
			break;
			case REL_PLUS_LMT:
			break;
			case REL_PLUS_MKT:
			break;
			case STP_LMT:
			break;
			case STP_PRT:
			break;
			case TRAIL:
			break;
			case TRAIL_LIMIT:
			break;
			case TRAIL_LIT:
			break;
			case TRAIL_LMT_PLUS_MKT:
			break;
			case TRAIL_MIT:
			break;
			case TRAIL_REL_PLUS_MKT:
			break;
			case VOL:
			break;
			case VWAP:
			break;
			default:
			break;
		}
	}

	/*	--------------------------------------------------------
	 * 
	 *	--------------------------------------------------------
	 */
	public void place(IBController ctrl) {

		int oid_main = main_order.submit(ctrl, false);

		// important!
		take_profit_order.parentId(oid_main); // this might already be set!? Check!
		take_profit_order.submit(ctrl, false);

		// important!
		cut_loss_order.parentId(oid_main); // this might already be set!? Check!
		cut_loss_order.submit(ctrl, false);
	}
}
