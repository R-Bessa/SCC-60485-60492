package scc.serverless;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import scc.db.DB;
import scc.utils.RedisCache;
import scc.utils.Token;

import static scc.db.DB.shortsDB;
import static scc.serverless.HttpFunction.TUKANO_RECOMMENDS;
import static scc.serverless.HttpFunction.TUKANO_SECRET;
import static scc.utils.RedisCache.REDIS_CACHE_ON;
import static scc.utils.Result.errorOrValue;

public class TimerFunction {
	private static final String TIMER_FUNCTION_NAME = "timerFunction";
	private static final String TIMER_TRIGGER_NAME = "timerFunctionTrigger";
	private static final String TIMER_TRIGGER_SCHEDULE = "0 * * * * *";
	
	@FunctionName(TIMER_FUNCTION_NAME)
	public void run(
	  @TimerTrigger(name = TIMER_TRIGGER_NAME, schedule = TIMER_TRIGGER_SCHEDULE) String timerInfo,
	      ExecutionContext context
	 ) {
		Token.setSecret(TUKANO_SECRET);

		if(REDIS_CACHE_ON)
			RedisCache.writeBackViews();

		var res = DB.getPopular();
		if(res.isOK()) {
			var shrt = res.value().get(0);
			System.out.println("TUKANO: " + shrt.getShortId());
			shrt.setShortId("tukano+" + shrt.getShortId());
			errorOrValue(DB.insertOne(res.value().get(0), shortsDB), s -> s.copyWithLikes_And_Token(0));
		}

	     context.getLogger().info("Timer was triggered: " + timerInfo);
	}
}
