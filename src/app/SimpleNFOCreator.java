package app;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import comparator.WindowsExplorerFileComparator;
import gui.entry.DirectoryEntry;
import gui.props.UIEntryProps;
import gui.props.variable.StringVariable;
import process.NonStandardProcess;
import process.ProcessManager;
import process.io.ProcessStreamSiphon;
import ui.log.LogDialog;
import ui.log.LogFileSiphon;
import xml.XMLUtils;

public class SimpleNFOCreator extends JFrame {

	private static final long serialVersionUID = 1L;

	private UIEntryProps props = new UIEntryProps();
	
	private GenerateNFO gen;

	public SimpleNFOCreator() {
		this.setTitle( "SimpleNFOCreator" );
		this.setSize( new Dimension( 420, 270 ) );
		this.setDefaultCloseOperation( EXIT_ON_CLOSE );
		this.setLayout( new BorderLayout() );
		
		props.addVariable( "sourceDir", new StringVariable( "D:/" ) );
		props.addVariable( "process", new StringVariable( "NFOCreator" ) );
		
		gen = new GenerateNFO( props.getString( "process" ) );
		this.add( dirPanel(), BorderLayout.CENTER );
		JButton b = new JButton( "Generate NFOs" );
		b.addActionListener( e -> gen.execute() );
		this.add( b, BorderLayout.SOUTH );
		this.setVisible( true );
	}

	private JPanel dirPanel() {
		JPanel p = new JPanel();
		p.setLayout( new BoxLayout( p, BoxLayout.Y_AXIS ) );
		p.add( new DirectoryEntry( "Source Dir:", props.getVariable( "sourceDir" ) ) );
		return p;
	}

	public static void main( String args[] ) {
		try {
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		} catch ( ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e ) {
			System.err.println( "Critical JVM Failure!" );
			e.printStackTrace();
		}
		new SimpleNFOCreator();
	}
	
	private class GenerateNFO extends NonStandardProcess {

		public GenerateNFO( String name ) {
			super( name );
		}
		
		public void execute() {
			try {
				String runName = props.getString( "process" );
				LogFileSiphon log = new LogFileSiphon( runName, props.getString( "sourceDir" ) + "/NFOCreator.log" ) {
					public void skimMessage( String name, String s ) {
						try {
							fstream.write( "[" + sdf.format( new Date( System.currentTimeMillis() ) ) + "]:  " + s );
							fstream.newLine();
							fstream.flush();
						} catch ( IOException e ) {
							e.printStackTrace();
						}
					}
				};
				new LogDialog( SimpleNFOCreator.this, runName, false );
				File f = new File( props.getString( "sourceDir" ) );
				generate( f );
				sendMessage( "Completed generating NFO files in: " + f.getAbsolutePath() );
				log.notifyProcessEnded( name );
				ProcessManager.getInstance().removeAll( name );
			} catch ( Exception e ) {
				e.printStackTrace();
			}
			for ( ProcessStreamSiphon siphon : ProcessManager.getInstance().getSiphons( name ) ) {
				siphon.notifyProcessEnded( name );
			}
		}
		
		private void generate( File f ) {
			List<File> files = Arrays.asList( f.listFiles() );
			files.sort( new WindowsExplorerFileComparator() );
			for ( File d : files ) {
				if ( d.isFile() && !d.getName().endsWith( ".log" ) && !d.getName().equals( "Thumbs.db" ) && ( d.getName().endsWith( ".mkv" ) || d.getName().endsWith( ".webm" ) || d.getName().endsWith( ".avi" ) || d.getName().endsWith( ".mp4" ) ) ) {
					File out = new File( d.getAbsolutePath().substring( 0, d.getAbsolutePath().lastIndexOf( "." ) ) + ".nfo" );
					if ( !out.exists() ) {
						writeFile( d, out );
					} else {
						gen.sendMessage( "nfo file exists for:  " + out.getName() + " skipping to next" );
					}
				}
			}
		}
		
		private void writeFile( File in, File out ) {
			String[] full = in.getName().substring( 0, in.getName().lastIndexOf( "." ) ).split( "-" );
			String episode = "";
			String season = "";
			String title = full[ full.length - 1 ].trim();
			for ( String s : full ) {
				String t = s.trim();
				if ( t.matches( "[sS](\\d{2,})[eE](\\d{2,})" ) ) {
					int i = t.indexOf( "e" );
					if ( i == -1 ) {
						i = t.indexOf( "E" );
					}
					season = String.valueOf( Integer.parseInt( t.substring( 1, i ) ) );
					episode = String.valueOf( Integer.parseInt( t.substring( i + 1 ) ) );
				}
			}
			try ( PrintWriter write = new PrintWriter( out ) ) {
				write.print( XMLUtils.wrapInTag( "\n\t" + XMLUtils.wrapInTag( title, "title" ) + "\n\t" + XMLUtils.wrapInTag( season, "season" ) + "\n\t" + XMLUtils.wrapInTag( episode, "episode" ) + "\n", "episodedetails" ) );
				gen.sendMessage( "wrote nfo for:  " + out.getName() );
			} catch ( FileNotFoundException e ) {
				StringWriter sw = new StringWriter();
				e.printStackTrace( new PrintWriter( sw ) );
				gen.sendMessage( sw.toString() );
				e.printStackTrace();
			}
		}	
	}
}