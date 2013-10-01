package com.nhn.pinpoint.profiler.modifier.arcus;

import java.security.ProtectionDomain;
import java.util.List;

import com.nhn.pinpoint.profiler.Agent;
import com.nhn.pinpoint.profiler.interceptor.Interceptor;
import com.nhn.pinpoint.profiler.interceptor.ScopeDelegateSimpleInterceptor;
import com.nhn.pinpoint.profiler.interceptor.SimpleAroundInterceptor;
import com.nhn.pinpoint.profiler.interceptor.bci.*;
import com.nhn.pinpoint.profiler.modifier.AbstractModifier;
import com.nhn.pinpoint.profiler.modifier.arcus.interceptor.ArcusScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author netspider
 */
public class ArcusClientModifier extends AbstractModifier {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	public ArcusClientModifier(ByteCodeInstrumentor byteCodeInstrumentor,
			Agent agent) {
		super(byteCodeInstrumentor, agent);
	}

	public String getTargetClass() {
		return "net/spy/memcached/ArcusClient";
	}

	public byte[] modify(ClassLoader classLoader, String javassistClassName,
			ProtectionDomain protectedDomain, byte[] classFileBuffer) {
		if (logger.isInfoEnabled()) {
            logger.info("Modifing. {}", javassistClassName);
		}

		try {
			InstrumentClass arcusClient = byteCodeInstrumentor.getClass(javassistClassName);

			final Interceptor setCacheManagerInterceptor = byteCodeInstrumentor.newInterceptor(classLoader,protectedDomain,"com.nhn.pinpoint.profiler.modifier.arcus.interceptor.SetCacheManagerInterceptor");
            final String[] args = {"net.spy.memcached.CacheManager"};
            arcusClient.addInterceptor("setCacheManager", args, setCacheManagerInterceptor, Type.before);

			// 모든 public 메소드에 ApiInterceptor를 적용한다.
			String[] ignored = new String[] { "__", "shutdown" };
            List<Method> declaredMethods = arcusClient.getDeclaredMethods(new ArcusMethodFilter(ignored));
            for (Method method : declaredMethods) {

                SimpleAroundInterceptor apiInterceptor = (SimpleAroundInterceptor) byteCodeInstrumentor.newInterceptor(classLoader, protectedDomain,
								"com.nhn.pinpoint.profiler.modifier.arcus.interceptor.ApiInterceptor");
                ScopeDelegateSimpleInterceptor arcusScopeDelegateSimpleInterceptor = new ScopeDelegateSimpleInterceptor(apiInterceptor, ArcusScope.SCOPE);
                arcusClient.addInterceptor(method.getMethodName(), method.getMethodParams(), arcusScopeDelegateSimpleInterceptor, Type.around);
			}

			return arcusClient.toBytecode();
		} catch (Exception e) {
			if (logger.isWarnEnabled()) {
                logger.warn(e.getMessage(), e);
			}
			return null;
		}
	}

}