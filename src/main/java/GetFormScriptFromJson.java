import com.lsfusion.actions.generate.GenerateFormJSONAction4lsf;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;


public class GetFormScriptFromJson extends InternalAction  {

      public GetFormScriptFromJson(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }
    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException,SQLHandledException {
        String f = (String) getParam(0, context);
        GenerateFormJSONAction4lsf generator  = new GenerateFormJSONAction4lsf();
        generator.prefixNonLatinVar = (String) getParam(1, context);
        try {
            findProperty("formScriptResult").change(generator.generate(f),context);
        } catch (ScriptingErrorLog.SemanticErrorException  ignored) {
        }
    }

}
