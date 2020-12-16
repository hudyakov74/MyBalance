import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class CheckJpg  extends InternalAction {
    public CheckJpg(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }
    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws  SQLException,SQLHandledException {
        RawFileData f =  (RawFileData)getParam(0, context);
        try {
            findProperty("isJPG").change((f.getBytes()[0]==-1)&&f.getBytes()[1]==-40,context);
        } catch (ScriptingErrorLog.SemanticErrorException  ignored) {
        }
    }

}

