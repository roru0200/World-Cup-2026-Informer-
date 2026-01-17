package bgu.spl.net.srv;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import bgu.spl.net.impl.stomp.StompFrame;
public class ConnectionsImpl<T> implements Connections<T> {

    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> handlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> channels = new ConcurrentHashMap<>();
    
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
        ConcurrentHashMap<Integer, String> subscribers = channels.get(channel);

        if (subscribers != null) {
            for (Map.Entry<Integer, String> entry : subscribers.entrySet()) {

                if (msg instanceof StompFrame) 
                    ((StompFrame) msg).getHeaders().put("subscription", entry.getValue());
                
                send(entry.getKey(), msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        // TODO: הסירו את החיבור ממפת ה-Handlers הפעילים.
        // TODO: עברו על כל הערוצים והסירו את המשתמש הזה מרשימות המנויים, אם הוא מופיע שם.
        ConnectionHandler<T> handler = handlers.remove(connectionId);
        if(handler != null){
            for(ConcurrentHashMap<Integer, String> subscribers : channels.values()){
                subscribers.remove(connectionId);
            }
        }
        
    }

    // --- פונקציות עזר (לא ב-Interface, אבל הכרחיות לפרוטוקול) ---

    /**
     * פונקציה שהשרת יקרא לה כשלקוח חדש מתחבר, כדי לרשום אותו ב-Connections
     */
    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        // TODO: הוסיפו את החיבור למבנה הנתונים שלכם.
        handlers.put(connectionId, handler);
    }

    /**
     * פונקציה שהפרוטוקול יקרא לה כשמתקבל פריימים של SUBSCRIBE
     */
    public void subscribe(String channel, int connectionId, String subscriptionId) {
        // TODO: הוסיפו את המשתמש לרשימת המנויים של הערוץ הספציפי.
        // שימו לב: ייתכן והערוץ עדיין לא קיים במבנה הנתונים, צריך ליצור אותו אם הוא חסר.
        channels.computeIfAbsent(channel, k -> new ConcurrentHashMap<>()).put(connectionId, subscriptionId); //creates a new channel entry if its not yet created
    }

    /**
     * פונקציה שהפרוטוקול יקרא לה כשמתקבל פריימים של UNSUBSCRIBE
     */
    public void unsubscribe(String channel, int connectionId) {
        // TODO: הסירו את המשתמש מרשימת המנויים של הערוץ.
        ConcurrentHashMap<Integer, String> tempChannel = channels.get(channel);
        if(tempChannel != null)
            tempChannel.remove(connectionId);
    }
}