
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRRewindableDataSource;

public class RewindableDSWrapper implements JRRewindableDataSource {

   private final JRRewindableDataSource ds;

  //public RewindableDSWrapper(JRRewindableDataSource ds) {
  public RewindableDSWrapper() {

    this.ds =    new JREmptyDataSource();
//    this.ds = ds;
//
////    try {
////        this.ds.moveFirst();
////    } catch (JRException e) {
////        e.printStackTrace();
////    }
//
   }

  public boolean next() throws JRException {
    return ds.next();
  }

   public Object getFieldValue(JRField jrField) throws JRException {
      return ds.getFieldValue(jrField);
  }

  public void moveFirst() throws JRException {
      ds.moveFirst();
    }
}