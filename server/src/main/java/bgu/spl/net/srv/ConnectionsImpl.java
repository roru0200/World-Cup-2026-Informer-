package bgu.spl.net.srv;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
public class ConnectionsImpl<T> implements Connections<T> {

    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> handlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> channels = new ConcurrentHashMap<>();
    
    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = handlers.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false; 
    }

    @Override
    public void send(String channel, T msg) { //brodacast to all subscribers in the channel
        ConcurrentLinkedQueue<Integer> subscribers = channels.get(channel);
        if (subscribers != null) {
            for (Integer connectionId : subscribers) {
                send(connectionId, msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        // TODO: הסירו את החיבור ממפת ה-Handlers הפעילים.
        // TODO: עברו על כל הערוצים והסירו את המשתמש הזה מרשימות המנויים, אם הוא מופיע שם.
    }

    // --- פונקציות עזר (לא ב-Interface, אבל הכרחיות לפרוטוקול) ---

    /**
     * פונקציה שהשרת יקרא לה כשלקוח חדש מתחבר, כדי לרשום אותו ב-Connections
     */
    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        // TODO: הוסיפו את החיבור למבנה הנתונים שלכם.
    }

    /**
     * פונקציה שהפרוטוקול יקרא לה כשמתקבל פריימים של SUBSCRIBE
     */
    public void subscribe(String channel, int connectionId) {
        // TODO: הוסיפו את המשתמש לרשימת המנויים של הערוץ הספציפי.
        // שימו לב: ייתכן והערוץ עדיין לא קיים במבנה הנתונים, צריך ליצור אותו אם הוא חסר.
    }

    /**
     * פונקציה שהפרוטוקול יקרא לה כשמתקבל פריימים של UNSUBSCRIBE
     */
    public void unsubscribe(String channel, int connectionId) {
        // TODO: הסירו את המשתמש מרשימת המנויים של הערוץ.
    }
}