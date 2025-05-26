package com.gmail.visualbukkit.extensions.bstats;

import com.gmail.visualbukkit.blocks.BlockDefinition;
import com.gmail.visualbukkit.blocks.StatementBlock;
import com.gmail.visualbukkit.blocks.parameters.ExpressionParameter;
import com.gmail.visualbukkit.project.BuildInfo;
import com.gmail.visualbukkit.reflection.ClassInfo;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

@BlockDefinition(
        id = "stat-connect-bstats",
        name = "Connect bStats",
        description = "Connects to bStats for statistics collection."
)
public class StatConnectBstats extends StatementBlock {

    public StatConnectBstats() {
        addParameter("ID", new ExpressionParameter(ClassInfo.of(int.class)));
    }

    @Override
    public String generateJava(BuildInfo buildInfo) {
        buildInfo.addClass(Roaster.parse(JavaClassSource.class, BstatsExtension.METRICS_CLASS));
        return "new Metrics(this," + arg(0, buildInfo) + ");";
    }
}
