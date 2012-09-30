package com.snobwall.transilook;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.snobwall.transilook.ui.ViewPanel;


public class Main {

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        
        SwingUtilities.invokeLater(new Runnable() {
            
            @Override
            public void run() {
                JFrame frame = new JFrame();
                
                frame.setSize(600, 600);
                frame.getContentPane().add(new ViewPanel());
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });
    }

}
