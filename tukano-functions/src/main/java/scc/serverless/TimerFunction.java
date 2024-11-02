package scc.serverless;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import static scc.utils.RedisCache.REDIS_CACHE_ON;

public class TimerFunction {
	private static final String TIMER_FUNCTION_NAME = "timerFunctionExample";
	private static final String TIMER_TRIGGER_NAME = "timerFunctionTrigger";
	private static final String TIMER_TRIGGER_SCHEDULE = "*/5 * * * *";
	
	@FunctionName(TIMER_FUNCTION_NAME)
	public void run(
	  @TimerTrigger(name = TIMER_TRIGGER_NAME, schedule = TIMER_TRIGGER_SCHEDULE) String timerInfo,
	      ExecutionContext context
	 ) {

		if(REDIS_CACHE_ON)
			System.out.println(); //TODO write back

		// TUKANO recommends select and post

	     context.getLogger().info("Timer was triggered: " + timerInfo);
	}
}
