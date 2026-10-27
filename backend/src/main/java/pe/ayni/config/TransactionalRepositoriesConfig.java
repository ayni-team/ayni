package pe.ayni.config;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Every repository call runs inside a transaction (TS01).
 *
 * <p>The university reaches PostgreSQL once per transaction, as ADR 0007 asks: {@link
 * TenantTransactionManager} sets {@code app.tenant_id} when one begins, never on the connection.
 * Spring Data only wraps in a transaction the methods its base class implements; a query method
 * declared on a repository interface, called from outside a use case, would run on a connection
 * with no university and row level security would answer it with nothing, even with the
 * university bound.
 *
 * <p>So each repository gets one more advice, after the one Spring Data already has: a read only
 * transaction that joins whichever is open, and only begins one when there is none. A method that
 * declares its own {@code @Transactional} keeps it, because Spring Data's advice runs first and
 * this one joins it.
 */
@Component
class TransactionalRepositoriesConfig implements BeanPostProcessor, BeanFactoryAware {

  private BeanFactory beanFactory;

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof RepositoryFactoryBeanSupport<?, ?, ?> repository) {
      repository.addRepositoryFactoryCustomizer(
          factory ->
              factory.addRepositoryProxyPostProcessor(
                  (proxy, information) -> proxy.addAdvice(readOnlyUnlessJoined())));
    }
    return bean;
  }

  private TransactionInterceptor readOnlyUnlessJoined() {
    DefaultTransactionAttribute attribute =
        new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_REQUIRED);
    attribute.setReadOnly(true);
    MatchAlwaysTransactionAttributeSource source = new MatchAlwaysTransactionAttributeSource();
    source.setTransactionAttribute(attribute);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionAttributeSource(source);
    interceptor.setTransactionManagerBeanName("transactionManager");
    interceptor.setBeanFactory(beanFactory);
    return interceptor;
  }
}
