package com.campuscanteen.config;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.PaymentService;

/**
 * Runs once when the application starts:
 * 1. Sends a "ping" to MongoDB to prove the connection really works.
 * 2. Creates any missing collections (existing ones and their data are left untouched).
 * 3. Creates the indexes (creating an index that already exists does nothing).
 * 4. Creates the default canteen if there is none.
 * 5. Once only: the default meal sessions and the draft food list (see StarterMenuSetup).
 */
@Component
@Order(1)
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    public static final List<String> COLLECTIONS = List.of(
            "users", "canteens", "menuItems", "mealSessions", "dailyMenus", "orders", "payments", "tokenCounters");

    private final MongoTemplate mongo;
    private final CanteenService canteenService;
    private final PaymentService paymentService;
    private final StarterMenuSetup starterMenuSetup;

    public DatabaseInitializer(MongoTemplate mongo, CanteenService canteenService, PaymentService paymentService,
                               StarterMenuSetup starterMenuSetup) {
        this.mongo = mongo;
        this.canteenService = canteenService;
        this.paymentService = paymentService;
        this.starterMenuSetup = starterMenuSetup;
    }

    @Override
    public void run(ApplicationArguments args) {
        checkConnection();
        createCollections();
        createIndexes();
        canteenService.ensureDefaultCanteen();
        starterMenuSetup.run();
        paymentService.reconcilePaymentRecords();
        log.info("Database setup complete.");
    }

    private void checkConnection() {
        try {
            mongo.getDb().runCommand(new Document("ping", 1));
            log.info("Connected to MongoDB. Using database '{}'.", mongo.getDb().getName());
        } catch (RuntimeException e) {
            // Only a plain explanation is logged, never the connection string.
            String explanation = MongoConnectionProblem.explain(e);
            log.error("Could not connect to MongoDB ({}). {}", e.getClass().getSimpleName(), explanation);
            // The driver's own error (attached as the cause) names the servers but never the password.
            throw new IllegalStateException("Could not connect to MongoDB. " + explanation, e);
        }
    }

    private void createCollections() {
        for (String name : COLLECTIONS) {
            if (!mongo.collectionExists(name)) {
                mongo.createCollection(name);
                log.info("Created collection '{}'.", name);
            }
        }
    }

    private void createIndexes() {
        // users: one account per college ID (IDs are stored in upper case, so "cs101" and "CS101" clash).
        IndexOperations users = mongo.indexOps("users");
        users.createIndex(new Index().on("collegeId", Sort.Direction.ASC).unique().named("collegeId_unique"));
        users.createIndex(new Index().on("role", Sort.Direction.ASC).named("role"));

        mongo.indexOps("menuItems").createIndex(new Index()
                .on("isActive", Sort.Direction.ASC).on("category", Sort.Direction.ASC).on("name", Sort.Direction.ASC)
                .named("active_category_name"));

        mongo.indexOps("mealSessions").createIndex(new Index()
                .on("canteenId", Sort.Direction.ASC).on("startTime", Sort.Direction.ASC).named("canteen_start"));

        // dailyMenus: only one menu per canteen, date and meal session.
        mongo.indexOps("dailyMenus").createIndex(new Index()
                .on("canteenId", Sort.Direction.ASC).on("date", Sort.Direction.ASC).on("mealSessionId", Sort.Direction.ASC)
                .unique().named("canteen_date_session_unique"));

        IndexOperations orders = mongo.indexOps("orders");
        orders.createIndex(new Index().on("orderNumber", Sort.Direction.ASC).unique().named("orderNumber_unique"));
        // The active FIFO queue query uses this index.
        orders.createIndex(new Index()
                .on("canteenId", Sort.Direction.ASC).on("orderStatus", Sort.Direction.ASC)
                .on("queueEnteredAt", Sort.Direction.ASC).on("tokenNumber", Sort.Direction.ASC)
                .named("queue_fifo"));
        orders.createIndex(new Index()
                .on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("user_orders"));
        orders.createIndex(new Index()
                .on("canteenId", Sort.Direction.ASC).on("orderDate", Sort.Direction.ASC).on("orderStatus", Sort.Direction.ASC)
                .named("canteen_date_status"));
        // Safety net: the database itself refuses two orders with the same token on the same day.
        orders.createIndex(new Index()
                .on("canteenId", Sort.Direction.ASC).on("tokenDate", Sort.Direction.ASC).on("tokenNumber", Sort.Direction.ASC)
                .unique().partial(PartialIndexFilter.of(Criteria.where("tokenNumber").exists(true)))
                .named("token_unique_per_day"));
        // One order per checkout: a double click or resubmitted checkout form cannot create a second order.
        orders.createIndex(new Index()
                .on("userId", Sort.Direction.ASC).on("checkoutKey", Sort.Direction.ASC)
                .unique().partial(PartialIndexFilter.of(Criteria.where("checkoutKey").exists(true)))
                .named("checkout_unique"));

        IndexOperations payments = mongo.indexOps("payments");
        payments.createIndex(new Index().on("orderId", Sort.Direction.ASC).unique().named("orderId_unique"));
        payments.createIndex(new Index()
                .on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC).named("status_created"));

        mongo.indexOps("tokenCounters").createIndex(new Index()
                .on("canteenId", Sort.Direction.ASC).on("date", Sort.Direction.ASC).unique().named("canteen_date_unique"));
    }
}
