package ai.grakn;

import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Entity;
import ai.grakn.concept.Label;
import ai.grakn.concept.Resource;
import ai.grakn.graql.MatchQuery;
import ai.grakn.graql.Order;
import ai.grakn.graql.Var;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.admin.Answer;
import ai.grakn.graql.analytics.PathQuery;
import com.ldbc.driver.DbException;
import com.ldbc.driver.OperationHandler;
import com.ldbc.driver.ResultReporter;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery1;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery13;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery13Result;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery1Result;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery2;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery2Result;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery8;
import com.ldbc.driver.workloads.ldbc.snb.interactive.LdbcQuery8Result;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static ai.grakn.graql.Graql.compute;
import static ai.grakn.graql.Graql.lte;
import static ai.grakn.graql.Graql.match;
import static ai.grakn.graql.Graql.or;
import static ai.grakn.graql.Graql.var;

/**
 *
 */
public class GraknQueryHandlers {

    static VarPattern knowsType = var().label("knows");
    static VarPattern hasCreatorType = var().label("has-creator");
    static VarPattern replyOf = var().label("reply-of");
    static VarPattern reply = var().label("reply");
    static VarPattern isLocatedIn = var().label("is-located-in");
    static VarPattern workAt = var().label("work-at");
    static VarPattern studyAt = var().label("study-at");

    static Label personID = Label.of("person-id");
    static Label creationDate = Label.of("creation-date");
    static Label messageID = Label.of("message-id");
    static Label personFirstName = Label.of("first-name");
    static Label personLastName = Label.of("last-name");
    static Label messageContent = Label.of("content");
    static Label messageImageFile = Label.of("image-file");
    static Label personBirthday = Label.of("birth-day");
    static Label gender = Label.of("gender");
    static Label browserUsed = Label.of("browser-used");
    static Label locationIp = Label.of("location-ip");
    static Label email = Label.of("email");
    static Label language = Label.of("language");
    static Label name = Label.of("name");

    static Var thePerson = var("person");
    static Var aMessage = var("aMessage");
    static Var aMessageDate = var("aMessageDate");
    static Var aMessageId = var("aMessageID");
    static Var someContent = var("content");
    static Var aFriend = var("aFriend");
    static Var aFriendId = var("aFriendID");
    static Var aFriendLastName = var("aFriendLastName");
    static Var aFriendGender = var("aFriendGender");

    // method to get the value of a resource from an answer
    private static <T> T resource(Answer result, Var resource) {
        return result.get(resource).<T>asResource().getValue();
    }

    private static long timeResource(Answer result, Var resource) {
        return result.get(resource).<LocalDateTime>asResource().getValue().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
    public static class LdbcQuery2Handler implements OperationHandler<LdbcQuery2, GraknDbConnectionState> {

        @Override
        public void executeOperation(LdbcQuery2 ldbcQuery2, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graknGraph = session.open(GraknTxType.READ)) {
                Var aFriendFirstName = var("aFriendFirstName");
                LocalDateTime maxDate = LocalDateTime.ofInstant(ldbcQuery2.maxDate().toInstant(), ZoneOffset.UTC);

                // to make this query execute faster split it into two parts:
                //     the first does the ordering
                //     the second fetches the resources
                MatchQuery graknLdbcQuery2 = match(
                        var().rel(thePerson.has(personID, var().val(ldbcQuery2.personId()))).rel(aFriend).isa(knowsType),
                        var().rel(aFriend).rel(aMessage).isa(hasCreatorType),
                        aMessage.has(creationDate, aMessageDate).has(messageID, aMessageId),
                        aMessageDate.val(lte(maxDate)));

                List<Answer> rawResult = graknLdbcQuery2.orderBy(aMessageDate, Order.desc)
                        .limit(ldbcQuery2.limit()).withGraph(graknGraph).execute();

                // sort first by date and then by message id
                Comparator<Answer> ugly = Comparator.<Answer>comparingLong(
                        map -> timeResource(map, aMessageDate)).reversed().
                        thenComparingLong(map -> resource(map, aMessageId));

                // process the query results
                List<LdbcQuery2Result> result = rawResult.stream().sorted(ugly).map(map -> {
                    // fetch the resources attached to entities in the queries
                    MatchQuery queryExtendedInfo = match(
                            aFriend.has(personFirstName, aFriendFirstName).has(personLastName, aFriendLastName).has(personID, aFriendId),
                            var().rel(aFriend).rel(aMessage).isa(hasCreatorType),
                            aMessage.has(creationDate, aMessageDate)
                                    .has(messageID, var().val(GraknQueryHandlers.<Long>resource(map, aMessageId))),
                            or(aMessage.has(messageContent, someContent), aMessage.has(messageImageFile, someContent)));
                    Answer extendedInfo = queryExtendedInfo.withGraph(graknGraph).execute().iterator().next();

                    // prepare the answer from the original query and the query for extended information
                    return new LdbcQuery2Result(
                            resource(extendedInfo, aFriendId),
                            resource(extendedInfo, aFriendFirstName),
                            resource(extendedInfo, aFriendLastName),
                            resource(map, aMessageId),
                            resource(extendedInfo, someContent),
                            timeResource(map, aMessageDate));
                }).collect(Collectors.toList());

                resultReporter.report(0,result,ldbcQuery2);
            }
        }

    }

    public static class LdbcQuery8Handler implements OperationHandler<LdbcQuery8, GraknDbConnectionState> {
        @Override
        public void executeOperation(LdbcQuery8 ldbcQuery8, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graknGraph = session.open(GraknTxType.READ)) {
                // for speed the query is again split into the ordering and limit phase
                Var aReply = var("aReply");
                Var responder = var("responder");
                Var responderId = var("responderId");
                Var responderFirst = var("responderFirst");
                Var responderLast = var("responderLast");
                MatchQuery orderQuery = match(
                        thePerson.has(personID, var().val(ldbcQuery8.personId())),
                        var().rel(thePerson).rel(aMessage).isa(hasCreatorType),
                        var().rel(aMessage).rel(reply, aReply).isa(replyOf),
                        aReply.has(creationDate, aMessageDate).has(messageID, aMessageId)
                );
                List<Answer> rawResult = orderQuery.withGraph(graknGraph)
                        .orderBy(aMessageDate, Order.desc).limit(ldbcQuery8.limit()).execute();

                // sort first by date and then by message id
                Comparator<Answer> ugly = Comparator.<Answer>comparingLong(
                        map -> timeResource(map, aMessageDate)).reversed().
                        thenComparingLong(map -> resource(map, aMessageId));

                // process the query results
                List<LdbcQuery8Result> result = rawResult.stream().sorted(ugly).map(map -> {
                    // fetch the resources attached to entities in the queries
                    MatchQuery queryExtendedInfo = match(
                            aReply.has(messageID, var().val(GraknQueryHandlers.<Long>resource(map, aMessageId))),
                            or(aReply.has(messageContent, someContent), aReply.has(messageImageFile, someContent)),
                            var().rel(aReply).rel(responder).isa(hasCreatorType),
                            responder.has(personID, responderId).has(personFirstName, responderFirst).has(personLastName, responderLast)
                            );
                    Answer extendedInfo = queryExtendedInfo.withGraph(graknGraph).execute().iterator().next();

                    // prepare the answer from the original query and the query for extended information
                    return new LdbcQuery8Result(
                            resource(extendedInfo, responderId),
                            resource(extendedInfo, responderFirst),
                            resource(extendedInfo, responderLast),
                            timeResource(map, aMessageDate),
                            resource(map, aMessageId),
                            resource(extendedInfo, someContent));
                }).collect(Collectors.toList());

                resultReporter.report(0,result,ldbcQuery8);
            }
        }
    }

    public static class LdbcQuery1Handler implements OperationHandler<LdbcQuery1, GraknDbConnectionState> {
        @Override
        public void executeOperation(LdbcQuery1 ldbcQuery1, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graknGraph = session.open(GraknTxType.READ)) {
                Var anyone = var("anyone");
                Var anyoneElse = var("anyoneElse");
                Var aFriendBirthday = var("aFriendBirthday");
                Var aFriendCreationDate = var("aFriendCreationDate");

                match(thePerson.has(personID, var().val(ldbcQuery1.personId()))).withGraph(graknGraph).
                        execute();
                // for speed fetch the Grakn id first
                ConceptId graknPersonId = match(thePerson.has(personID, var().val(ldbcQuery1.personId()))).withGraph(graknGraph).
                        execute().iterator().next().get(thePerson).getId();

                // sort by lastname and then id
                Comparator<Answer> ugly = Comparator.<Answer>comparingLong(
                        map -> resource(map, aFriendLastName)).
                        thenComparingLong(map -> resource(map, aFriendId));

                // This query has to be split into 3 parts, each fetching people a further distance away
                // The longer queries only need be executed if there are not enough shorter queries
                // The last ordering by id must be done after each query has been executed
                MatchQuery matchQuery = match(thePerson.id(graknPersonId),
                        var().rel(thePerson).rel(aFriend).isa(knowsType),
                        aFriend.has(personFirstName,var().val(ldbcQuery1.firstName())).
                                has(personLastName,aFriendLastName).
                                has(personID, aFriendId),
                        thePerson.neq(aFriend));
                List<Answer> distance1Result = matchQuery.withGraph(graknGraph).orderBy(aFriendLastName, Order.asc).execute();
                List<LdbcQuery1Result> distance1LdbcResult = distance1Result.stream().limit(ldbcQuery1.limit()).map(map -> {
                    // this query gets all of the additional related material, excluding resources
                    Var aLocation = var("aLocation");
                    MatchQuery additionalInfo = match(
                            aFriend.has(personID, getSingleResource(map.get(aFriend).asEntity(), personID, graknGraph)),
                            var().rel(aFriend).rel(aLocation).isa(isLocatedIn));
                    Answer additionalResult = additionalInfo.withGraph(graknGraph).execute().iterator().next();

                    // populate the result with resources using graphAPI and relations from additional info query
                    return new LdbcQuery1Result(
                            resource(map, aFriendId),
                            resource(map, aFriendLastName),
                            1,
                            getSingleDateResource(map.get(aFriend).asEntity(), personBirthday, graknGraph),
                            getSingleDateResource(map.get(aFriend).asEntity(), creationDate, graknGraph),
                            getSingleResource(map.get(aFriend).asEntity(), gender, graknGraph),
                            getSingleResource(map.get(aFriend).asEntity(), browserUsed, graknGraph),
                            getSingleResource(map.get(aFriend).asEntity(), locationIp, graknGraph),
                            getListResources(map.get(aFriend).asEntity(), email, graknGraph),
                            getListResources(map.get(aFriend).asEntity(), language, graknGraph),
                            getSingleResource(additionalResult.get(aLocation).asEntity(), name, graknGraph),
                            new ArrayList<List<Object>>(),
                            new ArrayList<List<Object>>());
                }).collect(Collectors.toList());
                if (distance1Result.size() < ldbcQuery1.limit()) {
                    matchQuery = match(thePerson.id(graknPersonId),
                            var().rel(thePerson).rel(anyone).isa(knowsType),
                            var().rel(anyone).rel(aFriend).isa(knowsType),
                            aFriend.has(personFirstName,var().val(ldbcQuery1.firstName())).has(personLastName,aFriendLastName),
                            thePerson.neq(aFriend)
                            );
                    List<Answer> distance2Result = matchQuery.withGraph(graknGraph).orderBy(aFriendLastName, Order.asc).execute();
//                    if (distance1Result.size() + distance2Result.size() < ldbcQuery1.limit()) {
//                        matchQuery = match(thePerson.id(graknPersonId),
//                                var().rel(thePerson).rel(anyone).isa(knowsType),
//                                var().rel(anyone).rel(anyoneElse).isa(knowsType),
//                                var().rel(anyoneElse).rel(aFriend).isa(knowsType),
//                                aFriend.has(personFirstName,var().val(ldbcQuery1.firstName())).has(personLastName,aFriendLastName),
//                                thePerson.neq(aFriend),
//                                aFriend.neq(anyone)
//                        );
//                        List<Answer> distance3Result = matchQuery.withGraph(graknGraph).orderBy(aFriendLastName, Order.asc).execute();
//                    }
                }
                resultReporter.report(0, distance1LdbcResult, ldbcQuery1);
            }
        }

        private <T> T getSingleResource(Entity entity, Label resourceType, GraknGraph graknGraph) {
            return (T) entity.resources(graknGraph.getResourceType(resourceType.toString())).
                    iterator().next().getValue();
        }
        private long getSingleDateResource(Entity entity, Label resourceType, GraknGraph graknGraph) {
            return ((LocalDateTime) getSingleResource(entity, resourceType, graknGraph)).
                    toInstant(ZoneOffset.UTC).toEpochMilli();
        }
        private <T> List<T> getListResources(Entity entity, Label resourceType, GraknGraph graknGraph) {
            Collection<Resource<?>> rawResources = entity.resources(graknGraph.getResourceType(resourceType.toString()));
            return rawResources.stream().map((resource) -> (T) resource.getValue()).collect(Collectors.<T>toList());
        }
    }

    public static class LdbcQuery13Handler implements OperationHandler<LdbcQuery13, GraknDbConnectionState> {
        @Override
        public void executeOperation(LdbcQuery13 ldbcQuery13, GraknDbConnectionState dbConnectionState, ResultReporter resultReporter) throws DbException {
            GraknSession session = dbConnectionState.session();
            try (GraknGraph graknGraph = session.open(GraknTxType.READ)) {
                MatchQuery matchQuery = match(thePerson.has(personID,var().val(ldbcQuery13.person1Id())));
                Concept person1 = matchQuery.withGraph(graknGraph).execute().iterator().next().get(thePerson);
                matchQuery = match(thePerson.has(personID,var().val(ldbcQuery13.person2Id())));
                Concept person2 = matchQuery.withGraph(graknGraph).execute().iterator().next().get(thePerson);

                PathQuery pathQuery = compute().path().from(person1.getId()).to(person2.getId())
                        .in("knows", "person");

                List<Concept> path = pathQuery.withGraph(graknGraph).execute().orElseGet(ArrayList::new);

                // our path is either:
                //     empty if there is none
                //     one if source = destination
                //     2*l+1 where l is the length of the path
                int l = path.size()-1;
                LdbcQuery13Result result;
                if (l<1) {result = new LdbcQuery13Result(l);}
                else {result = new LdbcQuery13Result(l/2);}

                resultReporter.report(0, result, ldbcQuery13);
            }
        }
    }
}
