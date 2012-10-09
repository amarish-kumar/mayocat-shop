package org.mayocat.shop.store.datanucleus;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import javax.jdo.JDODataStoreException;
import javax.jdo.JDOException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;

import org.mayocat.shop.model.HandleableEntity;
import org.mayocat.shop.model.event.EntityUpdatedEvent;
import org.mayocat.shop.store.StoreException;

public abstract class AbstractHandleableEntityStore<T extends HandleableEntity, K extends Serializable> extends
    AbstractEntityStore<T, K>
{

    public boolean exists(T entity) throws StoreException
    {
        return this.findByHandle(entity.getHandle()) != null;
    }

    public void update(T entity) throws StoreException
    {
        T existing = this.findByHandle(entity.getHandle());
        try {
            PersistenceManager pm = persistenceManager.get();
            Transaction tx = pm.currentTransaction();
            try {
                tx.begin();
                this.copyPersistentFields(existing, entity);
                tx.commit();

                this.observationManager.notify(new EntityUpdatedEvent(), this, entity);

            } catch (JDOException e) {
                this.logger.error("Failed to commit transaction", e);
                throw new StoreException("Failed to commit transaction", e);
            } finally {
                if (tx.isActive()) {
                    tx.rollback();
                }
            }

        } catch (Exception e) {
            throw new StoreException("Failed to update entity", e);
        }

    }

    public T findByHandle(String handle) throws StoreException
    {        
        PersistenceManager pm = null;
        Query q = null;
        try {
            pm = persistenceManager.get();

            q = pm.newQuery(this.getPersistentType());
            q.setFilter("handle == handleParam");
            q.declareParameters("String handleParam");

            List<T> results = (List<T>) q.execute(handle);
            if (results.size() == 1) {
                return results.get(0);
            }
            return null;

        } catch (JDODataStoreException e) {
            throw new StoreException(e);
        } finally {
            if (null != q) {
                q.closeAll();
            }
        }
    }

    private <O> void copyPersistentFields(Object existingEntity, O valueObject) throws IllegalAccessException,
        NoSuchMethodException, InvocationTargetException
    {
        // FIXME this is likely to be not very efficient
        // Check if equivalent behavior can be implemented without reflection,
        // using jdo state management.
        // See PersistenceCapable#jdoCopyFields(java.lang.Object, int[]) for example

        for (Method method : valueObject.getClass().getMethods()) {
            if (method.getName().startsWith("set") && Character.isUpperCase(method.getName().charAt(3))) {
                // Found a setter. Ensure it is accessible
                boolean setterAccessible = method.isAccessible();

                // Find the equivalent getter and ensure it is accessible
                Method getter;
                try {
                    getter = valueObject.getClass().getMethod("get" + method.getName().substring(3));
                }
                catch (java.lang.NoSuchMethodException e) {
                    // Try isXxx for booleans
                    getter = valueObject.getClass().getMethod("is" + method.getName().substring(3));
                }
                boolean getterAccessible = getter.isAccessible();
                getter.setAccessible(true);

                try {
                    // Obtain new value
                    Object value = getter.invoke(valueObject);

                    // If the value itself is a persistent object, recurse
                    if (value instanceof javax.jdo.spi.PersistenceCapable) {
                        this.copyPersistentFields(getter.invoke(existingEntity), value);
                    }
                    // Else if value not null, copy over to persistent object
                    else if (value != null) {
                        method.invoke(existingEntity, value);
                    }
                } finally {
                    // Tidy up
                    method.setAccessible(setterAccessible);
                    getter.setAccessible(getterAccessible);
                }
            }
        }
    }

}