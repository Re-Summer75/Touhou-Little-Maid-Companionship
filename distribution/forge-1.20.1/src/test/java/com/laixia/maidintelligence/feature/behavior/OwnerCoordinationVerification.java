package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.application.coordination.DefaultOwnerCoordinationService;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationAssignment;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationBid;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationGroupId;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.List;
import java.util.UUID;

public final class OwnerCoordinationVerification {
    private static final UUID OWNER = new UUID(0L, 1L);
    private static final UUID MAID_A = new UUID(0L, 10L);
    private static final UUID MAID_B = new UUID(0L, 20L);
    private static final UUID MAID_C = new UUID(0L, 30L);
    private static final OwnerCoordinationGroupId GROUP =
            new OwnerCoordinationGroupId(OWNER, "test:dimension");
    private static final OrchestrationId PURPOSE =
            new OrchestrationId("test", "shared_request");

    private OwnerCoordinationVerification() {
    }

    public static void main(String[] args) {
        verify();
    }

    private static void verify() {
        hardEligibilityAndStableTieBreakSelectOneResponder();
        fairnessAgingRotatesEquivalentResponders();
        fanOutAndStateBoundsAreEnforced();
    }

    private static void hardEligibilityAndStableTieBreakSelectOneResponder() {
        DefaultOwnerCoordinationService service =
                new DefaultOwnerCoordinationService();
        OwnerCoordinationRequest request = request(1L, 0L, 1);
        List<OwnerCoordinationBid> bids = List.of(
                bid(MAID_B, true, 100.0D, 4.0D, 0),
                bid(MAID_A, true, 100.0D, 4.0D, 0),
                bid(MAID_C, false, 1_000.0D, 0.0D, 0)
        );
        OwnerCoordinationAssignment assignment =
                service.assign(request, bids, 0L);
        require(assignment.responders().equals(List.of(MAID_A)),
                "Stable UUID tie-break or hard eligibility failed");

        OwnerCoordinationAssignment duplicate = service.assign(
                request,
                List.of(
                        bid(MAID_A, true, -100.0D, 100.0D, 50),
                        bid(MAID_B, true, 1_000.0D, 0.0D, 0)
                ),
                0L
        );
        require(duplicate.responders().equals(List.of(MAID_A)),
                "A live request changed responder after assignment");
    }

    private static void fairnessAgingRotatesEquivalentResponders() {
        DefaultOwnerCoordinationService service =
                new DefaultOwnerCoordinationService();
        List<OwnerCoordinationBid> equal = List.of(
                bid(MAID_A, true, 10.0D, 2.0D, 0),
                bid(MAID_B, true, 10.0D, 2.0D, 0)
        );
        require(service.assign(request(10L, 0L, 1), equal, 0L)
                        .assigned(MAID_A),
                "Initial deterministic responder was unexpected");
        require(service.assign(request(11L, 1L, 1), equal, 1L)
                        .assigned(MAID_B),
                "Fairness aging did not advance the waiting maid");
    }

    private static void fanOutAndStateBoundsAreEnforced() {
        DefaultOwnerCoordinationService service =
                new DefaultOwnerCoordinationService();
        OwnerCoordinationAssignment fanOut = service.assign(
                request(20L, 20L, 2),
                List.of(
                        bid(MAID_A, true, 30.0D, 1.0D, 0),
                        bid(MAID_B, true, 20.0D, 1.0D, 0),
                        bid(MAID_C, true, 10.0D, 1.0D, 0)
                ),
                20L
        );
        require(fanOut.responders().size() == 2
                        && fanOut.assigned(MAID_A)
                        && fanOut.assigned(MAID_B),
                "fanOut did not cap and rank responders");

        List<OwnerCoordinationBid> one = List.of(
                bid(MAID_A, true, 1.0D, 1.0D, 0)
        );
        for (int index = 0;
             index < DefaultOwnerCoordinationService
                     .MAX_REQUESTS_PER_GROUP + 20;
             index++) {
            long tick = 100L + index;
            service.assign(
                    new OwnerCoordinationRequest(
                            new UUID(100L, index),
                            GROUP,
                            PURPOSE,
                            1,
                            1,
                            tick,
                            tick + 1_000L
                    ),
                    one,
                    tick
            );
        }
        require(service.activeAssignments(GROUP, 250L).size()
                        <= DefaultOwnerCoordinationService
                        .MAX_REQUESTS_PER_GROUP,
                "Owner group retained unbounded requests");
        service.releaseCandidate(MAID_A, 251L);
        require(service.activeAssignments(GROUP, 251L).stream()
                        .noneMatch(value -> value.assigned(MAID_A)),
                "Departed maid retained an assignment");
    }

    private static OwnerCoordinationRequest request(
            long id,
            long gameTime,
            int fanOut
    ) {
        return new OwnerCoordinationRequest(
                new UUID(1L, id),
                GROUP,
                PURPOSE,
                10,
                fanOut,
                gameTime,
                gameTime
        );
    }

    private static OwnerCoordinationBid bid(
            UUID maid,
            boolean eligible,
            double utility,
            double distance,
            int load
    ) {
        return new OwnerCoordinationBid(
                maid,
                eligible,
                utility,
                distance,
                load
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
