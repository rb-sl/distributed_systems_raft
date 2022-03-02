package it.polimi.client.user;

/**
 * Class used to launch the client
 */
public class AppUser {
    /**
       Main for the client application
     */
    public static void main(String[] args) {
        User user;
        
        // args[0] -> clientName
        if(args.length == 1) {
            user = new User(args[0]);
        }
        else {
            user = new User();
        }
        
        user.startCmd();
    }
}
