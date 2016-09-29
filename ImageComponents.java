/**
 * ImageComponents.java
 * A5 solution by Graham Kelly (grahamtk) 
 * based on starter code by S. Tanimoto
 * Implemented Extra Credit
 * 
 * CSE 373, University of Washington, Winter 2016.
 * 
 */ 

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ByteLookupTable;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.LookupOp;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import javax.imageio.ImageIO;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ImageComponents extends JFrame implements ActionListener {
    public static ImageComponents appInstance; // Used in main().

    String startingImage = "gettysburg-address-p1.png";
    BufferedImage biTemp, biWorking, biFiltered; // These hold arrays of pixels.
    Graphics gOrig, gWorking; // Used to access the drawImage method.
    int w; // width of the current image.
    int h; // height of the current image.

    int[][] parentID; // For your forest of up-trees.
    private Map<Integer, Integer> componentNumber; // stores the progressiveColor number for each image component
    PriorityQueue<Edge> q; // for storing weighted edges in Kruskal alg implementation.
    
    JPanel viewPanel; // Where the image will be painted.
    JPopupMenu popup;
    JMenuBar menuBar;
    JMenu fileMenu, imageOpMenu, ccMenu, helpMenu;
    JMenuItem loadImageItem, saveAsItem, exitItem;
    JMenuItem lowPassItem, highPassItem, photoNegItem, RGBThreshItem;

    JMenuItem CCItem1, CCItem2;
    JMenuItem aboutItem, helpItem;
    
    JFileChooser fileChooser; // For loading and saving images.
    
    public class Color {
        int r, g, b;

        Color(int r, int g, int b) {
            this.r = r; this.g = g; this.b = b;    		
        }
        
        Color(int[] rgb) {
        	r = rgb[0]; g = rgb[1]; b = rgb[2];
        }
        
        Color(int rgba) {
        	r = (rgba & 0x00ff0000) >> 16;
        	g = (rgba & 0x0000ff00) >> 8;
        	b = rgba & 0x000000ff;
        }

        double squaredEuclideanDistance(Color c2) {
        	if (c2==null) { return Double.MAX_VALUE; }
			int dr = r-c2.r;
			int dg = g-c2.g;
			int db = b-c2.b;
			int sum_sq = dr*dr + dg*dg + db*db;
			return Math.sqrt(sum_sq);  
        }
        
		public int toInt() {
			return (((r<<8) + g) << 8)+b;
		}
    }
    
    /**
     * This class represents an edge in a weighted pixel graph.
     * Stores two endpoints and the weight of the edge.
     * @author grahamkelly
     */
    private class Edge implements Comparable<Edge> {
    	private int endpoint1, endpoint2;
    	private double weight;
    	
    	/**
    	 * initializes this Edge
    	 * @param endpoint1 (Edge): one endpoint
    	 * @param endpoint2 (Edge): another endpoint
    	 * @param weight (double): the weight of the edge
    	 */
    	public Edge(int endpoint1, int endpoint2, double weight) {
    		this.endpoint1 = endpoint1;
    		this.endpoint2 = endpoint2;
    		this.weight = weight;
    	}
    	
		
		/**
		 * @Override
		 * compares this edge to another. (standard procedure) for Comparable
		 */
		public int compareTo(Edge o) {
			double otherWeight = o.getWeight();
			if (otherWeight > weight) {
				return -1;
			} else if (otherWeight < weight) {
				return 1;
			} else if (o.getEndpoint1() == endpoint1 && o.getEndpoint2() == endpoint2) {
				return 0;
			} else if (o.getEndpoint1() > endpoint1) {
				return -1;
			} else if (o.getEndpoint2() > endpoint2) {
				return -1;
			} else {
				return 1;
			}
			
		}
		
		/**
		 * @return (double): the weight of this Edge
		 */
		public double getWeight() {
			return weight;
		}
		
		/**
		 * @return (int): the pixelID of endpoint1.
		 */
		public int getEndpoint1() {
			return endpoint1;
		}
		
		/**
		 * @return (int): the pixelID of endpoint2
		 */
		public int getEndpoint2() {
			return endpoint2;
		}
    	
    }


    // Some image manipulation data definitions that won't change...
    static LookupOp PHOTONEG_OP, RGBTHRESH_OP;
    static ConvolveOp LOWPASS_OP, HIGHPASS_OP;
    
    public static final float[] SHARPENING_KERNEL = { // sharpening filter kernel
        0.f, -1.f,  0.f,
       -1.f,  5.f, -1.f,
        0.f, -1.f,  0.f
    };

    public static final float[] BLURRING_KERNEL = {
        0.1f, 0.1f, 0.1f,    // low-pass filter kernel
        0.1f, 0.2f, 0.1f,
        0.1f, 0.1f, 0.1f
    };
    
    /**
     * computes x-coordinate of a given pixel
     * @param pixelID (int): the pixelID for which to find the x-coord
     * @return (int): the x-coord
     */
    private int getXCoord(int pixelID) {
    	return pixelID % w;
    }
    
    /**
     * computes y-coordinate of given pixel
     * @param pixelID (int): the pixelID for which to find the y-coord
     * @return (int): the y-coord
     */
    private int getYCoord(int pixelID) {
    	return pixelID / w;
    }
    
    /**
     * finds the current root for the passed pixel
     * @param pixelID (int): pixelID of the pixel (up tree)
     * @return (int): the pixelID of the root
     */
    int find(int pixelID) {
    	int x = getXCoord(pixelID);
    	int y = getYCoord(pixelID);
    	if (parentID[x][y] == -1) {
    		return pixelID;
    	} else {
    		return find(parentID[x][y]);
    	}
    }
    
    /**
     * joins two pixels s.t. pixel w/ smallest pixelID is the new root.
     * @param pixelID1 (int): pixelID of one pixel to consider
     * @param pixelID2 (int): pixelID of one pixel to consider
     */
    void union(int pixelID1, int pixelID2) {
    	if (pixelID1 < pixelID2) {
    		int x = getXCoord(pixelID2);
    		int y = getYCoord(pixelID2);
    		parentID[x][y] = pixelID1;
    	} else if (pixelID1 > pixelID2) {
    		int x = getXCoord(pixelID1);
    		int y = getYCoord(pixelID1);
    		parentID[x][y] = pixelID2;
    	} // do nothing if they're equal, though this is checked prior to call and should never be the case.
    }
    
    public ImageComponents() { // Constructor for the application.
        setTitle("Image Analyzer"); 
        addWindowListener(new WindowAdapter() { // Handle any window close-box clicks.
            public void windowClosing(WindowEvent e) {System.exit(0);}
        });

        // Create the panel for showing the current image, and override its
        // default paint method to call our paintPanel method to draw the image.
        viewPanel = new JPanel(){public void paint(Graphics g) { paintPanel(g);}};
        add("Center", viewPanel); // Put it smack dab in the middle of the JFrame.

        // Create standard menu bar
        menuBar = new JMenuBar();
        setJMenuBar(menuBar);
        fileMenu = new JMenu("File");
        imageOpMenu = new JMenu("Image Operations");
        ccMenu = new JMenu("Connected Components");
        helpMenu = new JMenu("Help");
        menuBar.add(fileMenu);
        menuBar.add(imageOpMenu);
        menuBar.add(ccMenu);
        menuBar.add(helpMenu);

        // Create the File menu's menu items.
        loadImageItem = new JMenuItem("Load image...");
        loadImageItem.addActionListener(this);
        fileMenu.add(loadImageItem);
        saveAsItem = new JMenuItem("Save as full-color PNG");
        saveAsItem.addActionListener(this);
        fileMenu.add(saveAsItem);
        exitItem = new JMenuItem("Quit");
        exitItem.addActionListener(this);
        fileMenu.add(exitItem);

        // Create the Image Operation menu items.
        lowPassItem = new JMenuItem("Convolve with blurring kernel");
        lowPassItem.addActionListener(this);
        imageOpMenu.add(lowPassItem);
        highPassItem = new JMenuItem("Convolve with sharpening kernel");
        highPassItem.addActionListener(this);
        imageOpMenu.add(highPassItem);
        photoNegItem = new JMenuItem("Photonegative");
        photoNegItem.addActionListener(this);
        imageOpMenu.add(photoNegItem);
        RGBThreshItem = new JMenuItem("RGB Thresholds at 128");
        RGBThreshItem.addActionListener(this);
        imageOpMenu.add(RGBThreshItem);

 
        // Create CC menu stuff.
        CCItem1 = new JMenuItem("Compute Connected Components and Recolor");
        CCItem1.addActionListener(this);
        CCItem2 = new JMenuItem("Segment Image and Recolor");
        CCItem2.addActionListener(this);
        ccMenu.add(CCItem1);
        ccMenu.add(CCItem2);
        
        // Create the Help menu's item.
        aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(this);
        helpMenu.add(aboutItem);
        helpItem = new JMenuItem("Help");
        helpItem.addActionListener(this);
        helpMenu.add(helpItem);

        // Initialize the image operators, if this is the first call to the constructor:
        if (PHOTONEG_OP==null) {
            byte[] lut = new byte[256];
            for (int j=0; j<256; j++) {
                lut[j] = (byte)(256-j); 
            }
            ByteLookupTable blut = new ByteLookupTable(0, lut); 
            PHOTONEG_OP = new LookupOp(blut, null);
        }
        if (RGBTHRESH_OP==null) {
            byte[] lut = new byte[256];
            for (int j=0; j<256; j++) {
                lut[j] = (byte)(j < 128 ? 0: 200);
            }
            ByteLookupTable blut = new ByteLookupTable(0, lut); 
            RGBTHRESH_OP = new LookupOp(blut, null);
        }
        if (LOWPASS_OP==null) {
            float[] data = BLURRING_KERNEL;
            LOWPASS_OP = new ConvolveOp(new Kernel(3, 3, data),
                                        ConvolveOp.EDGE_NO_OP,
                                        null);
        }
        if (HIGHPASS_OP==null) {
            float[] data = SHARPENING_KERNEL;
            HIGHPASS_OP = new ConvolveOp(new Kernel(3, 3, data),
                                        ConvolveOp.EDGE_NO_OP,
                                        null);
        }
        loadImage(startingImage); // Read in the pre-selected starting image.
        setVisible(true); // Display it.
        
        componentNumber = new HashMap<Integer, Integer>();
        q = new PriorityQueue<Edge>();
    }
    
    /**
     * creates an array to represent all the pixels in the current image.
     * initializes each element with the value -1 s.t. each pixel is its 
     * own root
     */
    private void initializeArray() {
    	parentID = new int[w][h];
        for (int i = 0; i < h; i++){
        	for (int j = 0; j < w; j++) {
        		parentID[j][i] = -1; // go row by row
        	}
        }
    }
    
    /**
     * computes the pixelID of passed x-, y-coord
     * pixelID = (width) * y + x
     * @param x (int): x-coord
     * @param y (int): y-coord
     * @return (int): the computed pixelID
     */
    int pixelID(int x, int y) {
    	return w * y + x;
    }
    
    /**
     * Given a path to a file on the file system, try to load in the file
     * as an image.  If that works, replace any current image by the new one.
     * Re-make the biFiltered buffered image, too, because its size probably
     * needs to be different to match that of the new image.
     */
    public void loadImage(String filename) {
        try {
            biTemp = ImageIO.read(new File(filename));
            w = biTemp.getWidth();
            h = biTemp.getHeight();
            viewPanel.setSize(w,h);
            initializeArray(); // our array is now reset w/ dimensions of the new image.
            biWorking = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            gWorking = biWorking.getGraphics();
            gWorking.drawImage(biTemp, 0, 0, null);
            biFiltered = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            pack(); // Lay out the JFrame and set its size.
            repaint();
        } catch (IOException e) {
            System.out.println("Image could not be read: "+filename);
            System.exit(1);
        }
    }

    /* Menu handlers
     */
    void handleFileMenu(JMenuItem mi){
        System.out.println("A file menu item was selected.");
        if (mi==loadImageItem) {
            File loadFile = new File("image-to-load.png");
            if (fileChooser==null) {
                fileChooser = new JFileChooser();
                fileChooser.setSelectedFile(loadFile);
                fileChooser.setFileFilter(new FileNameExtensionFilter("Image files", new String[] { "JPG", "JPEG", "GIF", "PNG" }));
            }
            int rval = fileChooser.showOpenDialog(this);
            if (rval == JFileChooser.APPROVE_OPTION) {
                loadFile = fileChooser.getSelectedFile();
                loadImage(loadFile.getPath());
            }
        }
        if (mi==saveAsItem) {
            File saveFile = new File("savedimage.png");
            fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(saveFile);
            int rval = fileChooser.showSaveDialog(this);
            if (rval == JFileChooser.APPROVE_OPTION) {
                saveFile = fileChooser.getSelectedFile();
                // Save the current image in PNG format, to a file.
                try {
                    ImageIO.write(biWorking, "png", saveFile);
                } catch (IOException ex) {
                    System.out.println("There was some problem saving the image.");
                }
            }
        }
        if (mi==exitItem) { this.setVisible(false); System.exit(0); }
    }

    void handleEditMenu(JMenuItem mi){
        System.out.println("An edit menu item was selected.");
    }

    void handleImageOpMenu(JMenuItem mi){
        System.out.println("An imageOp menu item was selected.");
        if (mi==lowPassItem) { applyOp(LOWPASS_OP); }
        else if (mi==highPassItem) { applyOp(HIGHPASS_OP); }
        else if (mi==photoNegItem) { applyOp(PHOTONEG_OP); }
        else if (mi==RGBThreshItem) { applyOp(RGBTHRESH_OP); }
        repaint();
    }

    void handleCCMenu(JMenuItem mi) {
        System.out.println("A connected components menu item was selected.");
        if (mi==CCItem1) { computeConnectedComponents(); }
        if (mi==CCItem2) { 
        	int nRegions = 25; // default value.
        	String inputValue = JOptionPane.showInputDialog("Please input the number of regions desired");
        	try {
        		nRegions = (new Integer(inputValue)).intValue();
        	}
        	catch(Exception e) {
        		System.out.println(e);
        		System.out.println("That did not convert to an integer. Using the default: 25.");
        	}
        	System.out.println("nregions is "+nRegions);
        // Call your image segmentation method here.
        	segmentImageAndRecolor(nRegions);
        }
    }
    void handleHelpMenu(JMenuItem mi){
        System.out.println("A help menu item was selected.");
        if (mi==aboutItem) {
            System.out.println("About: Well this is my program.");
            JOptionPane.showMessageDialog(this,
                "Image Components, Starter-Code Version.",
                "About",
                JOptionPane.PLAIN_MESSAGE);
        }
        else if (mi==helpItem) {
            System.out.println("In case of panic attack, select File: Quit.");
            JOptionPane.showMessageDialog(this,
                "To load a new image, choose File: Load image...\nFor anything else, just try different things.",
                "Help",
                JOptionPane.PLAIN_MESSAGE);
        }
    }

    /*
     * Used by Swing to set the size of the JFrame when pack() is called.
     */
    public Dimension getPreferredSize() {
        return new Dimension(w, h+50); // Leave some extra height for the menu bar.
    }

    public void paintPanel(Graphics g) {
        g.drawImage(biWorking, 0, 0, null);
    }
            	
    public void applyOp(BufferedImageOp operation) {
        operation.filter(biWorking, biFiltered);
        gWorking.drawImage(biFiltered, 0, 0, null);
    }

    public void actionPerformed(ActionEvent e) {
        Object obj = e.getSource(); // What Swing object issued the event?
        if (obj instanceof JMenuItem) { // Was it a menu item?
            JMenuItem mi = (JMenuItem)obj; // Yes, cast it.
            JPopupMenu pum = (JPopupMenu)mi.getParent(); // Get the object it's a child of.
            JMenu m = (JMenu) pum.getInvoker(); // Get the menu from that (popup menu) object.
            //System.out.println("Selected from the menu: "+m.getText()); // Printing this is a debugging aid.

            if (m==fileMenu)    { handleFileMenu(mi);    return; }  // Handle the item depending on what menu it's from.
            if (m==imageOpMenu) { handleImageOpMenu(mi); return; }
            if (m==ccMenu)      { handleCCMenu(mi);      return; }
            if (m==helpMenu)    { handleHelpMenu(mi);    return; }
        } else {
            System.out.println("Unhandled ActionEvent: "+e.getActionCommand());
        }
    }

    // Use this to put color information into a pixel of a BufferedImage object.
    // I DON'T USE THIS
    void putPixel(BufferedImage bi, int x, int y, int r, int g, int b) {
        int rgb = (r << 16) | (g << 8) | b; // pack 3 bytes into a word.
        bi.setRGB(x,  y, rgb);
    }
    
    /**
     * computes the connected components in the picture using the strict pixel graph.
     * recolors these components with high-contrasting colors so that they are easy
     * to see.
     */
    void computeConnectedComponents() {
	    int unionCount = findConnectedComponents();
    	System.out.println("The number of times that the method UNION was called for this image is: " + unionCount);
    	
    	int connectedCount = countConnectedComponents();
    	System.out.println("The number of connected components in this image is: " + connectedCount);
    	// testing: System.out.println("Should be: " + (w*h) + " Is: " + (connectedCount + unionCount));
    	
    	colorConnectedComponents();
    	repaint();
    	initializeArray(); // reset our array so subsequent method calls have fresh trees to work with.
    }
    
    /**
     * finds the connected components (again, under the strict pixel graph) and unions
     * the pixels within them together. counts how many unions.
     * @return (int): the number of unions executed.
     */
    private int findConnectedComponents() {
    	int unionCount = 0;
    	
    	for (int y = 0; y < h; y++) { // go row by row
    		for (int x = 0; x < w; x++) {
    			int currentColor = biWorking.getRGB(x, y);
    			int currentPixel = find(pixelID(x, y));
    			if (x+1 < w && currentColor == biWorking.getRGB(x+1, y)) {
    				// pixel on right is in same component
    				int testPixel = find(pixelID(x+1, y));
    				if (currentPixel != testPixel) {
    					union(currentPixel, testPixel);
    					unionCount++;
    				}
    			}
    			
    			if (y+1 < h && currentColor == biWorking.getRGB(x, y+1)) {
    				// pixel below is in same component
    				int testPixel = find(pixelID(x, y+1));
    				if (currentPixel != testPixel) {
    					union(currentPixel, testPixel);
    					unionCount++;
    				}
    			}
    		}
    	}
    	return unionCount;
    }
    
    /**
     * counts the number of connected components in the image and stores them w/
     * index (first component encountered = index 0, second encountered = index 1,
     * and so on..).
     * @return (int): the number of connected components
     */
    private int countConnectedComponents() {
    	int count = 0;
    	for (int y = 0; y < h; y++) {
    		for (int x = 0; x < w; x++) {
    			if (parentID[x][y] == -1) {
    				// this root represents a discrete component of the image.
    				componentNumber.put(pixelID(x, y), count);
    				count++;
    			}
    		}
    	}
    	System.out.println("Length of hashtable: " + componentNumber.keySet().size());
//		for testing (hopefully on small images):
//    	ArrayList<Integer> vals = new ArrayList<Integer>();
//    	for (Integer k : componentNumber.keySet()) {
//    		vals.add(componentNumber.get(k));
//    	}
//    	Collections.sort(vals);
//    	System.out.println(vals.toString());  	
    	return count;
    }
    
    /**
     * colors each connected component of the image
     */
    private void colorConnectedComponents() {
    	ProgressiveColors colorGen = new ProgressiveColors();
    	for (int y = 0; y < h; y++) {
    		for (int x = 0; x < w; x++) {
    			int key = find(pixelID(x, y));
    			int count = componentNumber.get(key).intValue();
    			int[] rgb = colorGen.progressiveColor(count);
    			Color color = new Color(rgb);
    			biWorking.setRGB(x, y, color.toInt());
    		}
    	}
    }
    
    /**
     * constructs a weighted pixel graph for the image and then derives a 
     * minimum spanning tree with as many roots as is passed. colors each
     * segment.
     * @param nRegions (int): the number of regions in final image
     */
    private void segmentImageAndRecolor(int nRegions) {
    	putEdges();
    	
    	int nTrees = w*h;
    	while (nTrees > nRegions) {
    		Edge e = q.remove();
    		// double weight = e.weight; // we don't actually use this!
    		
    		int root1 = find(e.getEndpoint1());
    		int root2 = find(e.getEndpoint2());
    		
    		if (root1 != root2) {
    			union(root1, root2);
    			nTrees -= 1;
    		}
    	}
    	
    	System.out.println("Done Finding minimum spanning forest.");
    	
    	countConnectedComponents();
    	colorConnectedComponents();
    	repaint();
    	initializeArray(); //reset our array so subsequent method calls have fresh trees to work with.
    }
    
    /**
     * computes and stores all the edges in the weighted pixel graph.
     */
    private void putEdges() {
    	for (int y = 0; y < (h); y++) {
    		for (int x = 0; x < (w); x++) {
    			Color current = new Color(biWorking.getRGB(x, y));
    			if (x+1 < w) {
    				// edge to the right
	    			q.add(new Edge(pixelID(x, y), pixelID(x+1, y), 
	    					current.squaredEuclideanDistance(new Color(biWorking.getRGB(x+1, y)))));
    			}
    			if (y+1 < h) {
    				// edge to the pixel below
	    			q.add(new Edge(pixelID(x, y), pixelID(x, y+1), 
	    					current.squaredEuclideanDistance(new Color(biWorking.getRGB(x, y+1)))));
    			}
    		}
    	}
    }

    /* This main method can be used to run the application. */
    public static void main(String s[]) {
    	javax.swing.SwingUtilities.invokeLater(new Runnable() {
    		public void run() {
    			appInstance = new ImageComponents();
    		}
    	});
        
    }
}
