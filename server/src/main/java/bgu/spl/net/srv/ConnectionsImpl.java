package bgu.spl.net.srv;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import bgu.spl.net.impl.stomp.StompFrame;
import bgu.spl.net.impl.data.*;

public class ConnectionsImpl<T> implements Connections<T> {

    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> handlers = new ConcurrentHashMap<>();// Map<connectionId, ConnectionHandler>
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> channels = new ConcurrentHashMap<>(); // Map<channel, Map<connectionId, subscriptionId>>
    private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<String>> userSubscriptions = new ConcurrentHashMap<>(); // Map<connectionId, List<channel>>
    private final Database db = Database.getInstance();
    
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
    public void send(String channel, T msg) {
        ConcurrentHashMap<Integer, String> subscribers = channels.get(channel);
        if (subscribers != null) {
            for (Integer connectionId : subscribers.keySet()) {
                send(connectionId, msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        // TODO: הסירו את החיבור ממפת ה-Handlers הפעילים.
        // TODO: עברו על כל הערוצים והסירו את המשתמש הזה מרשימות המנויים, אם הוא מופיע שם.
        handlers.remove(connectionId);
        ConcurrentLinkedQueue<String> userChannels = userSubscriptions.remove(connectionId);
        if(userChannels != null){
            for (String channel : userChannels) {
            channels.get(channel).remove(connectionId);
            }
        }
        db.logout(connectionId);
        
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
        userSubscriptions.computeIfAbsent(connectionId, k -> new ConcurrentLinkedQueue<>()).add(channel); //adds the channel to the user's list of subscriptions
    }

    /**
     * פונקציה שהפרוטוקול יקרא לה כשמתקבל פריימים של UNSUBSCRIBE
     */
    public void unsubscribe(String channel, int connectionId) {
        ConcurrentHashMap<Integer, String> tempChannel = channels.get(channel);
        if(tempChannel != null)
            tempChannel.remove(connectionId);
        ConcurrentLinkedQueue<String> userChannels = userSubscriptions.get(connectionId);
        if(userChannels != null)
            userChannels.remove(channel);
    }
    public ConcurrentHashMap<Integer, String> getSubscribers(String channel) {
        return channels.get(channel);
    }

    public LoginStatus login(int connectionId, String username, String password){
        return db.login(connectionId, username, password);
    }
}