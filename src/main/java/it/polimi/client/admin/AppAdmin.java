package it.polimi.client.admin;

public class AppAdmin {
    /**
     Main for the admin application
     */
    public static void main(String[] args) {
        AdminConsole admin;

        // args[0] -> adminName
        if(args.length == 1) {
            admin = new AdminConsole(args[0]);
        }
        else {
            admin = new AdminConsole();
        }
        
        admin.startCmd();
    }
}
