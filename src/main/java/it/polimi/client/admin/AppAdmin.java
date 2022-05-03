package it.polimi.client.admin;

public class AppAdmin {
    /**
     Main for the admin application
     */
    public static void main(String[] args) {
        Admin admin;

        // args[0] -> adminName
        if(args.length == 1) {
            admin = new Admin(args[0]);
        }
        else {
            admin = new Admin();
        }
        
        admin.startCmd();
    }
}
