package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;

/**
 * Swing client - File Storage
 * Client command: upload filename | download filename
 */

public class Client extends JFrame {
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;

    public Client() throws IOException {
        socket = new Socket("localhost", 6789);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());

        setSize(300,300);
        JPanel panel = new JPanel(new GridLayout(2,1));
        JButton btnSend = new JButton("SEND");
        JTextField textField = new JTextField();

        btnSend.addActionListener(a -> {
            String[] cmd = textField.getText().split(" ");
            if("upload".equals(cmd[0])){
                sendFile(cmd[1]);
            } else if("download".equals(cmd[0])) {
                getFile(cmd[1]);
            }
            
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                sendMessage("exit");
            }
        });

        panel.add(textField);
        panel.add(btnSend);
        add(panel);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void getFile(String filename) {
        //TODO 2 downloading
    }

    private void sendFile(String filename) {
        try{
            File file = new File("client/" + filename);
            if(!file.exists()){
                throw new FileNotFoundException();
            }
            long fileLength = file.length();
            FileInputStream fis = new FileInputStream(file);
            out.writeUTF("upload");
            out.writeUTF(filename);
            out.writeLong(fileLength);

            int read = 0;
            byte[] buffer = new byte[8 * 1024];
            while ((read = fis.read(buffer)) != -1){
            out.write(buffer, 0, read);
            }
            out.flush();

            String status = in.readUTF();
            System.out.println("Sending status: " + status);
        } catch (FileNotFoundException e) {
            System.err.println("File not found - /client/" + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String message) {
        try {
            out.writeUTF(message);
            String command = in.readUTF();
            /*if("done".equalsIgnoreCase(command)) {

            }*/
            System.out.println(command);
        }
        catch (EOFException eofException) {
            System.err.println("Reading command error from " + socket.getInetAddress());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) throws IOException {
        new Client();
    }
}
