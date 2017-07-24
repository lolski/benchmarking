package ai.grakn;

import ai.grakn.concept.Label;
import ai.grakn.graql.MatchQuery;
import ai.grakn.graql.Order;
import ai.grakn.graql.Var;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.admin.Answer;
import com.ldbc.driver.DbException;
import com.ldbc.driver.OperationHandler;
import com.ldbc.driver.ResultReporter;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery2;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery2Result;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static ai.grakn.graql.Graql.lte;
import static ai.grakn.graql.Graql.match;
import static ai.grakn.graql.Graql.var;

/**
 *
 */
public class GraknQueryHandlers {

    static VarPattern personType = var().label("person");
    static VarPattern aPerson = var("person").isa(personType);
    static VarPattern knowsType = var().label("knows");
    static VarPattern hasCreatorType = var().label("has-creator");

    static Label personID = Label.of("person-id");
    static Label messageDate = Label.of("creation-date");
    static Label messageID = Label.of("message-id");
    static Label personFirstName = Label.of("first-name");
    static Label personLastName = Label.of("last-name");

    public static class LdbcQuery1Handler implements OperationHandler<LdbcQuery2, GraknDbConnectionState> {

        @Override
        public void executeOperation(LdbcQuery2 ldbcQuery2, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graknGraph = session.open(GraknTxType.READ)) {
                VarPattern mainPerson = aPerson.has(personID, var().val(ldbcQuery2.personId()));
                Var aFriend = var("aFriend");
                Var aFriendId = var("aFriendID");
                Var aFriendFirstName = var("aFriendFirstName");
                Var aFriendLastName = var("aFriendLastName");
                VarPattern friendHasFirst = aFriend.has(personFirstName, aFriendFirstName);
                VarPattern friendHasLast = aFriend.has(personLastName, aFriendLastName);
                VarPattern friendHasID = aFriend.has(personID, aFriendId);
                VarPattern friendsOfMainPerson = var().rel(mainPerson).rel(aFriend).isa(knowsType);
                VarPattern aMessage = var("aMessage");
                VarPattern messagesOfFriend = var().rel(aFriend).rel(aMessage).isa(hasCreatorType);
                Var aMessageDate = var();
                Var aMessageId = var();
                VarPattern dateOfMessage = aMessage.has(messageDate, aMessageDate);
                VarPattern idOfMessage = aMessage.has(messageID, aMessageId);
                VarPattern filteredDate = aMessageDate.val(lte(LocalDateTime.ofInstant(ldbcQuery2.maxDate().toInstant(), ZoneOffset.UTC)));
                MatchQuery graknLdbcQuery1 = match(friendHasFirst, friendHasLast, friendHasID, friendsOfMainPerson, messagesOfFriend, dateOfMessage, idOfMessage, filteredDate);
                graknLdbcQuery1.orderBy(aMessageDate, Order.desc).limit(ldbcQuery2.limit());

                List<Answer> rawResult = graknLdbcQuery1.withGraph(graknGraph).execute();

                Comparator<Answer> ugly = Comparator.comparingLong(map -> map.get(aMessageId).<Long>asResource().getValue());
                List<LdbcQuery2Result> result = rawResult.stream().sorted(ugly).map(map -> new LdbcQuery2Result(
                        resource(map, aFriendId),
                        resource(map, aFriendFirstName),
                        resource(map, aFriendLastName),
                        resource(map, aMessageId),
                        "blah",
                        123L
                )).collect(Collectors.toList());
            }
        }

        private <T> T resource(Answer result, Var resource) {
            return result.get(resource).<T>asResource().getValue();
        }
    }
}
