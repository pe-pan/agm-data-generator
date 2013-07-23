package com.hp.demo.ali.rest;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.entity.User;
import org.apache.log4j.Logger;
import org.hp.almjclient.connection.ServiceResourceAdapter;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;
import org.hp.almjclient.services.ConnectionService;
import org.hp.almjclient.services.EntityCRUDService;
import org.hp.almjclient.services.KanbanStatusConfigurationService;
import org.hp.almjclient.services.impl.ConnectionManager;
import org.hp.almjclient.services.impl.ProjectServicesFactory;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/**
 * Created by panuska on 18.7.13.
 */
public class AgmRestService {
    private static Logger log = Logger.getLogger(RestClient.class.getName());
    private static ServiceResourceAdapter adapter;
    private ProjectServicesFactory factory;
    private static KanbanStatusConfigurationService kanbanService;
    private static String collectionBaseUrl;


    private static final int CONNECTION_TIMEOUT = 600000;

    public AgmRestService() {
        Settings settings = Settings.getSettings();
        User admin = User.getUser(settings.getAdmin());
        ConnectionService connection = ConnectionManager.getConnection(CONNECTION_TIMEOUT, null, null);
        connection.setTenantId(Integer.parseInt(settings.getTenantId()));
        try {
            connection.connect(settings.getRestUrl(), admin.getLogin(), admin.getPassword());
            factory = connection.getProjectServicesFactory(settings.getDomain(), settings.getProject());
            adapter = factory.getServiceResourceAdapter();
            kanbanService = factory.getKanbanStatusConfigurationSerive();
            collectionBaseUrl = factory.getProjectRestMetaData().getCollectionBaseUrl();
        } catch (RestClientException | ALMRestException e) {
            log.debug(e);
            throw new IllegalStateException(e);
        }
    }

    private static AgmRestService service = null;
    public static void initRestService() {
        service = new AgmRestService();
    }
    public static AgmRestService getCRUDService() {
        if (service == null) {
            log.error("Rest service not initialized yet!");
            throw new IllegalStateException("Rest service not initialized yet!");
        }
        return service;
    }

    public static ServiceResourceAdapter getAdapter() {
        return adapter;
    }

    public static KanbanStatusConfigurationService getKanbanStatusConfigurationService() {
        return kanbanService;
    }

    public static String getCollectionBaseUrl() {
        return collectionBaseUrl;
    }

    public static final int WAIT_TIME = 5000;

    public static final int DELETE_RETRIES = 1;
    public Entity delete(String entityType, final Integer entityId) throws RestClientException, ALMRestException {
        final EntityCRUDService entityService = factory.getEntityCRUDService(entityType);
        return new RetriableTask<>(DELETE_RETRIES, WAIT_TIME,
                new Callable<Entity>() {
                    public Entity call() throws ALMRestException, RestClientException {
                        return entityService.delete(entityId);
                    }
                }).call();
    }

    public static final int DELETE_COLLECTION_RETRIES = 1;
    public Entities delete(final String entityType, final Collection<Integer> idsToDelete, final boolean forceDeleteChildren) throws RestClientException, ALMRestException {
        final EntityCRUDService entityService = factory.getEntityCRUDService(entityType);
        return new RetriableTask<>(DELETE_COLLECTION_RETRIES, WAIT_TIME,
                new Callable<Entities>() {
                    public Entities call() throws ALMRestException, RestClientException {
                        return entityService.delete(entityType, idsToDelete, forceDeleteChildren);
                    }
                }).call();
    }

    public static final int READ_RETRIES = 5;
    public Entity read(String entityType, final Integer entityId) throws RestClientException, ALMRestException {
        final EntityCRUDService entityService = factory.getEntityCRUDService(entityType);
        return new RetriableTask<>(READ_RETRIES, WAIT_TIME,
                new Callable<Entity>() {
                    public Entity call() throws ALMRestException, RestClientException {
                        return entityService.read(entityId);
                    }
                }).call();
    }

    public static final int READ_COLLECTION_RETRIES = 5;
    public Entities readCollection(final Filter filter) throws RestClientException, ALMRestException {
        final EntityCRUDService entityService = factory.getEntityCRUDService(filter.getEntityType());
        return new RetriableTask<>(READ_COLLECTION_RETRIES, WAIT_TIME,
                new Callable<Entities >() {
                    public Entities call() throws ALMRestException, RestClientException {
                        return entityService.readCollection(filter);
                    }
                }).call();
    }

    public static final int CREATE_RETRIES = 5;
    public Entity create(final Entity entity) throws RestClientException, ALMRestException {
        final EntityCRUDService entityService = factory.getEntityCRUDService(entity.getType());
        return new RetriableTask<>(CREATE_RETRIES, WAIT_TIME,
                new Callable<Entity>() {
                    public Entity call() throws ALMRestException, RestClientException {
                        return entityService.create(entity);
                    }
                }).call();
    }

    public static final int UPDATE_RETRIES = 5;
    public Entity update(final Entity entity) throws RestClientException, ALMRestException {
        final EntityCRUDService entityService = factory.getEntityCRUDService(entity.getType());
        return new RetriableTask<>(UPDATE_RETRIES, WAIT_TIME,
                new Callable<Entity>() {
                    public Entity call() throws ALMRestException, RestClientException {
                        return entityService.update(entity);
                    }
                }).call();
    }

    //copied from http://fahdshariff.blogspot.com/2009/08/retrying-operations-in-java.html
    class RetriableTask<T> implements Callable<T> {

        private Callable<T> task;
        private int numberOfTriesLeft; // number left
        private long timeToWait; // wait interval

        public RetriableTask(int numberOfRetries, long timeToWait,
                             Callable<T> task) {
            numberOfTriesLeft = numberOfRetries;
            this.timeToWait = timeToWait;
            this.task = task;
        }

        public T call() throws ALMRestException, RestClientException {
            while (true) {
                try {
                    return task.call();
                } catch (CancellationException e) {
                    throw e;
                } catch (ALMRestException|RestClientException e) {
                    numberOfTriesLeft--;
                    if (numberOfTriesLeft == 0) {
                        throw e;
                    } else {
                        log.error("An exception caught; attempts to retry: " + numberOfTriesLeft);
                        log.error("Going to sleep for " + timeToWait + " ms");
                        log.debug(e);
                        try {
                            Thread.sleep(timeToWait);
                        } catch (InterruptedException e1) {
                            throw new IllegalStateException(e1);
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("This exception should never be thrown", e);
                }
            }
        }
    }
}
