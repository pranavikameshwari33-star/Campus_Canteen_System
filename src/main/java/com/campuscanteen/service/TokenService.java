package com.campuscanteen.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.campuscanteen.model.TokenCounter;

/**
 * Generates pickup tokens: 1, 2, 3, ... for each canteen, starting again at 1 every day.
 *
 * How it avoids duplicates: there is one counter document per canteen per day
 * (_id = "canteenId_yyyy-MM-dd"). MongoDB's findAndModify with $inc increases the counter
 * and returns the new value in a single atomic step, so two simultaneous requests can never
 * receive the same number. "upsert" creates the day's counter automatically on the first token,
 * which is why tokens reset daily without any scheduled job.
 */
@Service
public class TokenService {

    private final MongoTemplate mongo;

    public TokenService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public int nextToken(String canteenId, String date) {
        Query query = new Query(Criteria.where("_id").is(canteenId + "_" + date));
        Update update = new Update()
                .inc("lastTokenNumber", 1)
                .setOnInsert("canteenId", canteenId)
                .setOnInsert("date", date);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
        try {
            return mongo.findAndModify(query, update, options, TokenCounter.class).getLastTokenNumber();
        } catch (DuplicateKeyException e) {
            // Two requests tried to create the day's counter at the same moment; the other one won.
            // The counter now exists, so a second attempt simply increments it.
            return mongo.findAndModify(query, update, options, TokenCounter.class).getLastTokenNumber();
        }
    }
}
