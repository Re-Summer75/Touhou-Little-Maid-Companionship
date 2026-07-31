package com.laixia.maidintelligence.feature.physics.client.audit;

import com.laixia.maidintelligence.feature.physics.client.MeshPenetrationAuditAccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class MeshPenetrationAuditCli {
    private MeshPenetrationAuditCli() {
    }

    public static void run(String[] args) throws Exception {
        if (args.length == 2 && args[0].startsWith("trace:")) {
            MeshPenetrationDiagnostics.trace(
                    args[0].substring("trace:".length()), args[1]
            );
            return;
        }
        if (args.length == 2 && args[0].startsWith("chain:")) {
            MeshPenetrationChainAudit.run(
                    args[0].substring("chain:".length()), args[1]
            );
            return;
        }
        if (args.length == 2 && args[0].startsWith("proxies:")) {
            MeshPenetrationDiagnostics.proxies(
                    args[0].substring("proxies:".length()), args[1]
            );
            return;
        }
        List<Path> models;
        if (args.length > 0) {
            models = new ArrayList<>();
            for (String name : args) {
                models.add(
                        MeshPenetrationAuditAccess.modelDirectory()
                                .resolve(name)
                );
            }
        } else {
            try (Stream<Path> stream = Files.list(
                    MeshPenetrationAuditAccess.modelDirectory()
            )) {
                models = stream.filter(path -> path.getFileName()
                        .toString().endsWith(".json")).sorted().toList();
            }
        }
        System.out.printf(
                Locale.ROOT,
                "%-22s %4s %6s %7s %6s %5s %6s %5s %5s  %-16s %-16s %s%n",
                "model", "segs", "axis", "geom", "hits", "lyr",
                "buzz", "revs", "per", "worstAxis", "worstGeom", "worstJitter"
        );
        System.out.println("-".repeat(136));
        AuditRow total = new AuditRow();
        for (Path path : models) {
            total.accumulate(MeshPenetrationSummary.audit(path));
        }
        System.out.println("-".repeat(136));
        AuditRow.print("WORST OF " + models.size(), total);
    }
}
