package pe.ayni.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The transaction manager of the application: a JPA one that tells PostgreSQL which university the
 * transaction belongs to (TS01, ADR 0007).
 *
 * <p>Every transaction starts with {@code set_config('app.tenant_id', <university>, true)}, the
 * function form of {@code SET LOCAL}: the value lasts until the transaction ends and never outlives
 * it, so a pooled connection carries no university into the next request. The row level security
 * policies compare {@code tenant_id} with it. Without a university bound the value is empty, and
 * every tenant scoped table answers nothing: a forgotten filter, or a request without a
 * university, sees no rows instead of somebody else's.
 *
 * <p>The university can change while a transaction is open: a listener's transaction begins before
 * its body binds the university of its event, and a job binds one university after another. {@link
 * TenantContext} tells this class of every change, and the open transaction of the thread is set
 * again. What the transaction wrote until then is flushed first, so it reaches the database as the
 * university it was written for: Hibernate would otherwise send it at the commit, under whatever
 * university is bound by then, usually none. A transaction that is committing or rolling back is
 * left alone, and so is a thread with none open: the next one to begin reads the value then.
 */
public class TenantTransactionManager extends JpaTransactionManager {

  private static final long serialVersionUID = 1L;

  private static final String SET_TENANT = "select set_config('app.tenant_id', ?, true)";

  /** The transactions this thread has open, the innermost first. */
  private static final ThreadLocal<Deque<OpenTransaction>> OPEN = ThreadLocal.withInitial(ArrayDeque::new);

  static {
    TenantContext.onChange(TenantTransactionManager::tenantChanged);
  }

  private static final class OpenTransaction {
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    private boolean completing;

    private OpenTransaction(DataSource dataSource, EntityManagerFactory entityManagerFactory) {
      this.dataSource = dataSource;
      this.entityManagerFactory = entityManagerFactory;
    }
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {
    super.doBegin(transaction, definition);
    DataSource dataSource = getDataSource();
    try {
      apply(dataSource, TenantContext.get());
    } catch (SQLException failure) {
      throw new CannotCreateTransactionException("Could not bind the university", failure);
    }
    OPEN.get().push(new OpenTransaction(dataSource, obtainEntityManagerFactory()));
  }

  @Override
  protected void doCommit(DefaultTransactionStatus status) {
    markCompleting();
    super.doCommit(status);
  }

  @Override
  protected void doRollback(DefaultTransactionStatus status) {
    markCompleting();
    super.doRollback(status);
  }

  @Override
  protected void doCleanupAfterCompletion(Object transaction) {
    Deque<OpenTransaction> open = OPEN.get();
    if (!open.isEmpty()) {
      open.pop();
    }
    if (open.isEmpty()) {
      OPEN.remove();
    }
    super.doCleanupAfterCompletion(transaction);
  }

  private static void markCompleting() {
    OpenTransaction current = OPEN.get().peek();
    if (current != null) {
      current.completing = true;
    }
  }

  private static void tenantChanged(String tenantId) {
    Deque<OpenTransaction> open = OPEN.get();
    OpenTransaction current = open.peek();
    if (current == null) {
      OPEN.remove();
      return;
    }
    if (current.completing) {
      return;
    }
    if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        && TransactionSynchronizationManager.getResource(current.entityManagerFactory)
            instanceof EntityManagerHolder holder) {
      holder.getEntityManager().flush();
    }
    try {
      apply(current.dataSource, tenantId);
    } catch (SQLException failure) {
      throw new TransactionSystemException("Could not bind the university", failure);
    }
  }

  /** Runs on the connection of the open transaction, which is bound to the thread by now. */
  private static void apply(DataSource dataSource, String tenantId) throws SQLException {
    Connection connection = DataSourceUtils.getConnection(dataSource);
    try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
      statement.setString(1, tenantId == null ? "" : tenantId);
      statement.execute();
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }
}
