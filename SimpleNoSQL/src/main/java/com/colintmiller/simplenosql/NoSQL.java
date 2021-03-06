package com.colintmiller.simplenosql;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.colintmiller.simplenosql.threading.DataDispatcher;
import com.colintmiller.simplenosql.threading.QueryDelivery;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Simple access to a NoSQL store. You can save, retrieve, and delete any entity. Saving performs an insert
 * or an update (overwriting previous data). All calls are done asynchronously. Due to this, retrieving data
 * requires a callback.<p>
 *
 * Data is stored locally via serialization to a String. By default, the Gson library is used to convert objects to
 * JSON. You may provide custom serializers and deserializers by implementing the
 * {@link com.colintmiller.simplenosql.DataSerializer} and {@link com.colintmiller.simplenosql.DataDeserializer}
 * interfaces and supplying them on construction.
 */
public class NoSQL
{
    private static NoSQL singleton;

    private Context appContext;
    private DataSerializer singleSerializer;
    private DataDeserializer singleDeserializer;

    private final BlockingQueue<NoSQLQuery<?>> queryQueue;
    private DataDispatcher[] dispatchers;
    private QueryDelivery delivery;

    private NoSQL(Context context, int numberOfThreads) {
        this.appContext = context.getApplicationContext();
        queryQueue = new LinkedBlockingQueue<NoSQLQuery<?>>();
        dispatchers = new DataDispatcher[numberOfThreads]; //TODO: Add a thread pool size
        this.delivery = new QueryDelivery(new Handler(Looper.getMainLooper()));
        start();
    }

    /**
     * Get a NoSQL instance based on the given Context. Will use the applicationContext of the given context for
     * requests. This instance is safe to save and use later with the {@link NoSQL#using(Class)} method to create a
     * QueryBuilder. You may also register serializers and deserializers with this instance. Note, an instance of NoSQL
     * will ignore the serializer and deserializer set with the 'register' static methods.
     *
     * @param context to use for future operations.
     * @return a NoSQL object for creating queries.
     */
    public static NoSQL with(Context context) {
        return with(context, 4);
    }

    public static NoSQL with(Context context, int numberOfThreads) {
        if (singleton == null) {
            synchronized (NoSQL.class) {
                if (singleton == null) {
                    singleton = new NoSQL(context, numberOfThreads);
                }
            }
        }

        return singleton;
    }

    /**
     * Get a builder for performing some sort of data operation. This builder can be used for retrieval, saving, or
     * deletion of data. See {@link com.colintmiller.simplenosql.QueryBuilder} for more information on how to build
     * a query.
     *
     * @param clazz related to this operation. This would be the class of the objects you're saving or retrieving.
     * @param <T> the type of the ojbets used in this operation.
     * @return a {@link com.colintmiller.simplenosql.QueryBuilder}.
     */
    public <T> QueryBuilder<T> using(Class<T> clazz) {
        return withUsing(clazz, singleSerializer, singleDeserializer, queryQueue);
    }

    /**
     * By default, SimpleNoSQL will use Google's Gson library for serialization and deserialization. You may override
     * the serializer to use via this method. If you override both the serializer and deserializer before making any
     * data calls, you may safely remove gson as a dependency.
     *
     * @param serializer to use with this NoSQL instance.
     * @return this for chaining.
     */
    public NoSQL withSerializer(DataSerializer serializer) {
        this.singleSerializer = serializer;
        return this;
    }

    /**
     * By default, SimpleNoSQL will use Google's Gson library for serialization and deserialization. You may override
     * the deserializer to use via this method. If you override both the serializer and deserializer before making any
     * data calls, you may safely remove gson as a dependency.
     *
     * @param deserializer to use with this NoSQL instance.
     * @return this for chaining.
     */
    public NoSQL withDeserializer(DataDeserializer deserializer) {
        this.singleDeserializer = deserializer;
        return this;
    }

    public void start() {
        stop(); // in case there's already threads started.

        for(int i = 0; i < dispatchers.length; i++) {
            DataDispatcher dispatcher = new DataDispatcher(queryQueue, appContext, delivery);
            dispatchers[i] = dispatcher;
            dispatcher.start();
        }
    }

    public void stop() {
        for (DataDispatcher dispatcher : dispatchers) {
            if (dispatcher != null) {
                dispatcher.quit();
            }
        }
    }

    private static <T> QueryBuilder<T> withUsing(Class<T> clazz, DataSerializer serializer, DataDeserializer deserializer, BlockingQueue<NoSQLQuery<?>> queue) {
        QueryBuilder<T> builder = new QueryBuilder<T>(clazz, queue);
        if (serializer != null) {
            builder.serializer(serializer);
        }
        if (deserializer != null) {
            builder.deserializer(deserializer);
        }

        return builder;
    }

    @Override
    protected void finalize() throws Throwable {
        for (DataDispatcher dispatcher : dispatchers) {
            if (dispatcher != null) {
                dispatcher.quit();
            }
        }
        super.finalize();
    }
}
