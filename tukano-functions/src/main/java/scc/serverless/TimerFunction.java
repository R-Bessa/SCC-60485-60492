package scc.serverless;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import scc.utils.RedisCache;
import scc.utils.Token;

import static scc.serverless.HttpFunction.TUKANO_SECRET;
import static scc.utils.RedisCache.REDIS_CACHE_ON;

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

		// TUKANO recommends select and post

	     context.getLogger().info("Timer was triggered: " + timerInfo);
	}
}
