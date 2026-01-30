import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import net.sf.jasperreports.crosstabs.design.JRDesignCrosstab;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.PositionTypeEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;


public class MbJasperDuplicateCrosstabTemplate  extends InternalAction {

    public MbJasperDuplicateCrosstabTemplate(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        FileData fileIn         = (FileData) getParam(0, context);
        int      countCrosstab  = (int)      getParam(1, context);
        String   labelCrosstab  = (String)   getParam(2, context);

        try {
            JasperDesign design = JRXmlLoader.load(fileIn.getRawFile().getInputStream());
            // 1. шаблонный первый попавшийся
            JRDesignCrosstab crosstab = (JRDesignCrosstab) design.getSummary().getChildren().stream().filter(ct -> JRDesignCrosstab.class.isInstance(ct)).findAny().get();
            if (crosstab != null) {
                crosstab.getPropertiesMap().setProperty("com.jaspersoft.studio.element.name", labelCrosstab.concat(Integer.toString(1)));
                crosstab.setHeight(0);
                int maxH = crosstab.getY() + crosstab.getHeight();
                for (int y = 2; y <= countCrosstab; y++) {
                    JRDesignCrosstab newCt = (JRDesignCrosstab) crosstab.clone();
                    newCt.getPropertiesMap().setProperty("com.jaspersoft.studio.element.name", labelCrosstab.concat(Integer.toString(y)));
                    newCt.setY(maxH);
                    newCt.setPositionType(PositionTypeEnum.FLOAT);
                    maxH += newCt.getHeight();
                    design.getSummary().getChildren().add(newCt);
                }
                ((JRDesignBand) design.getSummary()).setHeight(maxH);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                JRXmlWriter.writeReport(design, out ,"UTF-8"); //ISO-8859-1   UTF-8
                RawFileData rf = new RawFileData(out);
                findProperty("bRepRawFile").change(rf, context);
            }
        } catch (JRException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}