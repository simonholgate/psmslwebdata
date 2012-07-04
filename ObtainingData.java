// Program to be run periodically to produce a new "obtaining data" page for
// the PSMSL website.
//
// Usage:
//    java -classpath /packages/oracle/product/10.2.0/jdbc/lib/classes12.zip:.\
//      ObtainingData
//
//
// Compilation:
//    javac ObtainingData.java
//
// The program produces a file named index.php which is actually a
// concatenation of the file header.php (which contains fixed information at
// the top of the page) and data obtained from the PSMSL database
//

import java.sql.*;
import java.io.*;
import java.util.Hashtable;
import java.util.Calendar;
import java.text.SimpleDateFormat;

public class ObtainingData {

//TODO create catalogue file
    //private static String base_path = "/login/simonh/work/www/wwwdev/";
    private static String base_path = "/data/obtaining/";

    public static void main(String[] args)
            throws Exception {

        ResultSet table_rs = null;
        String dbase = "LIA";
        //String dbase = "LID";
// Declare the JDBC objects.
        Connection con = null;
        PreparedStatement table_ps = null;

        try {
// Remove the old files before we create any new ones
            //System.out.println("Deleting files:");
            removeFiles();

// The first part of the program extracts the data for the table from
// the database
            con = getConnection(dbase);

// Get a PreparedStatement to send for processing
            table_ps = getTableStatement(con);
// Submit a query, creating a ResultSet object
            table_rs = getData(table_ps);

// Count rows
            /*table_rs.last();
            int rowcnt = table_rs.getRow();
            System.out.println("Total rows: " + Integer.valueOf(rowcnt).toString());*/

            //System.out.println("rlr table:");
            // Write all columns and rows from the result set as an HTML table in a string
            table_rs.beforeFirst();
            String rlrtable = tableData(table_rs, "rlr");

            // Write out the table
            File rlrFile = new File(base_path + "rlrtable.php");
            setContents(rlrFile, rlrtable);

            //System.out.println("metric table:");
            // Write all columns and rows from the result set as an HTML table in a string
            table_rs.beforeFirst();
            String metrictable = tableData(table_rs, "metric");

            // Write out the table
            File metricFile = new File(base_path + "metrictable.php");
            setContents(metricFile, metrictable);

            // Write station pages
            //System.out.println("station pages:");
            table_rs.beforeFirst();
            stationPages(table_rs);

            // Write documentation pages
            //System.out.println("documentation:");
            table_rs.beforeFirst();
            docuPages(table_rs);


            // Write authority documentation pages
            //System.out.println("authority documentation:");
            table_rs.beforeFirst();
            authPages(con, table_rs);

            // Get the data for stations
            //System.out.println("data:");
            table_rs.beforeFirst();
            Hashtable zeroIds;
            zeroIds = dataPages(table_rs, con);

            // Zip catalogue files
            //System.out.println("zip file lists:");
            table_rs.beforeFirst();
            fileLists(table_rs, zeroIds);

            // Data catalogue file
            //System.out.println("data catalogue:");
            table_rs.beforeFirst();
            cataloguePage(table_rs);

            // Conversion file
            //System.out.println("conversion file:");
            table_rs.beforeFirst();
            conversionPage(table_rs);

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
// Close the result sets
            if (table_rs != null) {
                try {
                    table_rs.close();
                } catch (Exception e) {
                    System.err.println(e.toString());
                }
            }
            if (table_ps != null) {
                try {
                    table_ps.close();
                } catch (Exception e) {
                    System.err.println(e.toString());
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (Exception e) {
                    System.err.println(e.toString());
                }
            }
        }
    }

    private static String annualPadString(String yearStr) {
        Object[] params;
        String padStr = new String();
        StringBuilder padSb = new StringBuilder();
        java.util.Formatter padFormatter = new java.util.Formatter(padSb, java.util.Locale.US);
        params = new Object[]{yearStr, "-99999", "N", "000"};
        padStr = padFormatter.format("%5s;%6s;%s;%s", params).toString();

        return padStr;

    }

    private static String monthlyPadString(Float yrFlo) {
        Object[] params;
        String padStr = new String();
        StringBuilder padSb = new StringBuilder();
        java.util.Formatter padFormatter = new java.util.Formatter(padSb, java.util.Locale.US);
        String padDateStr = new String();
        StringBuilder padDateSb = new StringBuilder();
        java.util.Formatter padDateFormatter = new java.util.Formatter(padDateSb, java.util.Locale.US);
        padDateFormatter = padDateFormatter.format("%11.4f", Float.valueOf(yrFlo));
        padDateStr = padDateFormatter.toString();

        params = new Object[]{padDateStr, "-99999", "00", "000"};
        padStr = padFormatter.format("%5s;%6s;%s;%s", params).toString();

        return padStr;

    }

    private static void removeFiles() throws IOException {
        try {
            // Delete old files first

            //System.out.println("Deleting rlr files annual data:");
            deleteFiles(base_path + "rlr.annual.data/", "^[0-9].*");

            //System.out.println("Deleting rlr annual file list:");
            deleteFiles(base_path + "rlr.annual.data/", "fileList.txt");

            //System.out.println("Deleting rlr files monthly data:");
            deleteFiles(base_path + "rlr.monthly.data/", "^[0-9].*");

            //System.out.println("Deleting rlr monthly file list:");
            deleteFiles(base_path + "rlr.monthly.data/", "fileList.txt");

            //System.out.println("Deleting metric monthly data:");
            deleteFiles(base_path + "met.monthly.data/", "^[0-9].*");

            //System.out.println("Deleting metric monthly file list:");
            deleteFiles(base_path + "met.monthly.data/", "fileList.txt");

            //System.out.println("Deleting metric documentation:");
            deleteFiles(base_path + "docu.psmsl/php/", "^[0-9].*");

            //System.out.println("Deleting rlr documentation:");
            deleteFiles(base_path + "docu.psmsl/", "^[0-9].*");

            //System.out.println("Deleting stations:");
            deleteFiles(base_path + "stations/", "^[0-9].*");

            //System.out.println("Deleting rlr diagrams:");
            deleteFiles(base_path + "rlr.diagrams/", "^[0-9].*");

            //System.out.println("Deleting rlr diagrams text:");
            deleteFiles(base_path + "rlr.diagrams/text/", "^[0-9].*");

        } catch (Exception e) {
            System.err.println(e.toString());
        }
    }

    /******************************************************************************
    // tableData - produces the Obtaining Data page with an HTML table of
    // the data extracted from the database
     ******************************************************************************/
    static private String tableData(java.sql.ResultSet rs, String table)
            throws Exception {

        StringBuilder contents = new StringBuilder();
        int rowCount = 0;

        try {
// Print table

// Table header
            contents.append("<table id=\"stationTable\" class=\"tablesorter\" >");

            contents.append("<thead>\n");
            contents.append("<tr>\n"
                    + "<th>Station Name</th>\n"
                    + "<th>ID</th>\n"
                    + "<th>Lat.</th>\n"
                    + "<th>Lon.</th>\n"
                    + "<th>GLOSS ID&nbsp;&nbsp;</th>\n"
                    + "<th>Country&nbsp;&nbsp;</th>\n"
                    + "<th>Date</th>\n"
                    + "<th>Coastline&nbsp;&nbsp;</th>\n"
                    + "<th>Station&nbsp;&nbsp;</th>\n"
                    + "</tr>\n"
                    + "</thead>\n"
                    + "<tbody>\n");

            String rowStr = new String();
            Object[] params;
// The data
            while (rs.next()) {
                rowCount++;
                String isrlr = rs.getString(11);
                
                String metricfirstyear = rs.getString(25);// Dummy get of string to see if it's null
                // Only write a station page at all if there is some data
                // i.e. metricfirstyear is not null
                if (!rs.wasNull()) {

                    if ((table.equals("rlr") && isrlr.equals("Y"))
                            || (table.equals("metric") && isrlr.equals("N"))) {

                        contents.append("<TR>");
                        /**
                        1 NAME, 2 ID, 3 LATITUDE,
                        4 LONGITUDE, 5 GLOSSID,
                        6 DATESTAMP, 7 COASTLINE, 8 STATIONCODE,
                        9 DOCUMENTATION, 10 SUPPLIERID,
                        11 ISRLR, 12 TEXT3, 13 COUNTRY,
                        14 SUPPLIER, 15 ADDRESS1, 16 ADDRESS2,
                        17 ADDRESS3, 18 ADDRESS4, 19 ADDRESS5, 20 ADDRESS6,
                        21 FIRSTYEAR, 22 LASTYEAR, 23 COMPLETENESS,
                        24 RLRDIAGRAMTEXT, 25 METRICFIRSTYEAR, 26 METRICLASTYEAR,
                        27 METRICCOMPLETENESS, 28 FREQUENCY, 29 QCFLAG
                         **/
// NAME
                        contents.append("<TD>" + rs.getString(1) + "</TD>");
// ID
                        Integer resInt = Integer.valueOf(rs.getInt(2));
                        params = new Object[]{resInt};
                        rowStr = String.format("%5d", params);
                        contents.append("<TD><P>"
                                + "<A HREF=\"./stations/" + resInt.toString() + ".php\">"
                                + rowStr + "</A></P></TD>");
// LAT
                        Float lat = new Float(rs.getFloat(3));
                        params = new Object[]{lat};
                        rowStr = String.format("% 7.3f", params);
                        contents.append("<TD><P>"
                                + rowStr + "</P></TD>");
// LON
                        Float lon = new Float(rs.getFloat(4));
                        params = new Object[]{lon};
                        rowStr = String.format("% 8.3f", params);
                        contents.append("<TD><P>"
                                + rowStr + "</P></TD>");
// GLOSSID
                        Integer glossInt = Integer.valueOf(rs.getInt(5));
                        params = new Object[]{glossInt};
                        rowStr = String.format("%7d", params);
                        if (glossInt.intValue() == 0) {
                            contents.append("<TD> </TD>");
                        } else {
                            contents.append("<TD><P>"
                                    + rowStr + "</P></TD>");
                        }
// TEXT3
                        contents.append("<TD><A HREF=\"#\" TITLE=\""
                                + rs.getString(13)
                                + "\" ONCLICK=\"return false\">" + rs.getString(12)
                                + "</A></TD>");
// DATESTAMP
                        Date dateStamp = rs.getDate(6);
                        if (rs.wasNull()) {
                            rowStr = "01/01/1980";
                        } else {
                            params = new Object[]{dateStamp, dateStamp, dateStamp};
                            rowStr = String.format("%td/%tm/%tY", params);
                        }
                        contents.append("<TD>" + rowStr + "</TD>");
// CCODE
                        Integer ccodeInt = Integer.valueOf(rs.getInt(7));
                        params = new Object[]{ccodeInt};
                        rowStr = String.format("% 04d", params);
                        contents.append("<TD>" + rowStr + "</TD>");
// SCODE
                        Integer scodeInt = Integer.valueOf(rs.getInt(8));
                        params = new Object[]{scodeInt};
                        rowStr = String.format("% 04d", params);
                        contents.append("<TD>" + rowStr + "</TD>");

                        contents.append("</TR>");
                        contents.append("\n");
                    }

                }
            }
            contents.append("</TBODY></TABLE></P>");
            contents.append("\n");

// Table sorting
            contents.append("<script type=\"text/javascript\">"
                    + "$(document).ready(function()"
                    + "{"
                    + "$(\"#stationTable\").tablesorter( {"
                    + "sortList: [[7,0], [8,0]]} );"
                    + "}"
                    + ");"
                    + "</script>\n");

//      System.out.println("RowCount: " + Integer.valueOf(rowCount).toString());

        } catch (Exception e) {
            System.err.println(e.toString());
        }

        return contents.toString();
    }

   /***************************************************************************
    // cataloguePage - produces the catalogue.dat page with a list of
    // the data in the database
    *
    * NOTE: A station ID greter than 4 characters and a supplier ID greater
    * than 4 characters will break the code.  The last is only true for the
    * nucat file.
    **************************************************************************/
    static private void cataloguePage(java.sql.ResultSet rs)
            throws Exception {
      
        int rowCount = 0;
        FileWriter catalogueFile = new FileWriter(base_path + "catalogue.dat", false);
        FileWriter nucatFile = new FileWriter(base_path + "nucat.dat", false);

        try {
// Print table
            StringBuilder contents = new StringBuilder();
            StringBuilder nucontents = new StringBuilder();

// Table header
            contents.append("                       CATALOGUE OF DATA HELD BY THE PSMSL.\n");

            contents.append("                       ------------------------------------\n");
            contents.append("\n\n");

            nucontents.append("                       CATALOGUE OF DATA HELD BY THE PSMSL.\n");

            nucontents.append("                       ------------------------------------\n");
            nucontents.append("\n\n");

            // Write out the catalogue
            
            catalogueFile.write(contents.toString());
            nucatFile.write(nucontents.toString());

            int coastlineInt;
            int lastCoastlineInt=0;

// The data
            while (rs.next()) {
                rowCount++;

                int metricfirstyear = rs.getInt(25);// Dummy get of string to see if it's null
                // Only write a station out if there is some data
                // i.e. metricfirstyear is not null
                if (!rs.wasNull()) {

                        /**
                        1 NAME, 2 ID, 3 LATITUDE,
                        4 LONGITUDE, 5 GLOSSID,
                        6 DATESTAMP, 7 COASTLINE, 8 STATIONCODE,
                        9 DOCUMENTATION, 10 SUPPLIERID,
                        11 ISRLR, 12 TEXT3, 13 COUNTRY,
                        14 SUPPLIER, 15 ADDRESS1, 16 ADDRESS2,
                        17 ADDRESS3, 18 ADDRESS4, 19 ADDRESS5, 20 ADDRESS6,
                        21 FIRSTYEAR, 22 LASTYEAR, 23 COMPLETENESS,
                        24 RLRDIAGRAMTEXT, 25 METRICFIRSTYEAR, 26 METRICLASTYEAR,
                        27 METRICCOMPLETENESS, 28 FREQUENCY, 29 QCFLAG
                         **/
                    contents = new StringBuilder();
                    nucontents = new StringBuilder();
                    String rowStr = new String();
                    String nurowStr = new String();
                    StringBuilder rowSb = new StringBuilder();
                    java.util.Formatter rowFormatter = new java.util.Formatter(rowSb, java.util.Locale.US);
                    StringBuilder nurowSb = new StringBuilder();
                    java.util.Formatter nurowFormatter = new java.util.Formatter(nurowSb, java.util.Locale.US);
                    Object[] params;
                    Object[] nuparams;

                    coastlineInt = rs.getInt(7);
                    
                    // Only print out the country line if it's a new country...
                    if (coastlineInt!=lastCoastlineInt){
                        /*if(coastlineInt==130){
                        System.out.println("coastLine: " + coastlineInt + " last coastline: " + lastCoastlineInt);
                        }*/
//                              010  ICELAND
                        params = new Object[]{" ", coastlineInt, rs.getString(13)};
                        rowStr = rowFormatter.format("%n%n%32s%03d  %-43s%n%n", params).toString();
                        nurowStr = nurowFormatter.format("%n%n%32s%03d  %-43s%n%n", params).toString();
                        params = new Object[]{"ID","SC","Station Name","Latitude","Longitude","SID","FC","GLO","Metric","PC","RLR","PC"};
                        nurowStr = nurowFormatter.format("%4s %3s %-40s %12s %13s %4s %2s %3s   %9s %7s   %9s %7s%n%n",params).toString();
                    }
                    lastCoastlineInt = coastlineInt;

// 001 REYKJAVIK                                64 09 N  21 56 W           55/24     GLOSS  229
                        Float lat = new Float(rs.getFloat(3));
                        Float lon = new Float(rs.getFloat(4));
                        String fcstr = rs.getString(28);
                        if (rs.wasNull()) {
                            fcstr = " ?";
                        }
                        params = new Object[]{rs.getInt(8), rs.getString(1), Math.abs(lat), Math.abs(lon), rs.getString(10), fcstr};
                        nuparams = new Object[]{rs.getInt(2),rs.getInt(8), rs.getString(1), Math.abs(lat), Math.abs(lon), rs.getString(10), fcstr};
                        if (lat >= 0e0) {
                            if (lon >= 0e0) {
                                rowStr = rowFormatter.format(" %03d %-40s%7.4f N %9.4f E %10s/%2s", params).toString();
                                nurowStr = nurowFormatter.format("%4d %03d %-40s %10.6f N %11.6f E %4s %2s", nuparams).toString();
                            } else {
                                rowStr = rowFormatter.format(" %03d %-40s%7.4f N %9.4f W %10s/%2s", params).toString();
                                nurowStr = nurowFormatter.format("%4d %03d %-40s %10.6f N %11.6f W %4s %2s", nuparams).toString();

                            }
                        } else {
                             if (lon >= 0e0) {
                                rowStr = rowFormatter.format(" %03d %-40s%7.4f S %9.4f E %10s/%2s", params).toString();
                                nurowStr = nurowFormatter.format("%4d %03d %-40s %10.6f S %11.6f E %4s %2s", nuparams).toString();
                            } else {
                                rowStr = rowFormatter.format(" %03d %-40s%7.4f S %9.4f W %10s/%2s", params).toString();
                                nurowStr = nurowFormatter.format("%4d %03d %-40s %10.6f S %11.6f W %4s %2s", nuparams).toString();

                            }
                        }
                        int glossID = rs.getInt(5);
                        if (!rs.wasNull()) {
                            params = new Object[]{glossID};
                            rowStr = rowFormatter.format("     GLOSS  %03d%n", params).toString();
                            nurowStr = nurowFormatter.format(" %03d", params).toString();
                        } else {
                            params = new Object[]{glossID};
                            rowStr = rowFormatter.format("%n", params).toString();
                            nurowStr = nurowFormatter.format("    ", params).toString();
                        }

                        params = new Object[]{"STATION ID  :", rs.getInt(2)};
                        rowStr = rowFormatter.format("%38s %4d%n", params).toString();

                        params = new Object[]{"DATA FOR  :", rs.getString(25), rs.getString(26)};
                        rowStr = rowFormatter.format("%38s %4s-%4s%n", params).toString();
                        nuparams = new Object[]{rs.getString(25), rs.getString(26)};
                        nurowStr = nurowFormatter.format("   %4s-%4s", nuparams).toString();

                        params = new Object[]{"METRIC COMPLETENESS  :", rs.getFloat(27)*100.0};
                        rowStr = rowFormatter.format("%38s %6.2f %%%n", params).toString();
                        nuparams = new Object[]{rs.getFloat(27)*100.0};
                        nurowStr = nurowFormatter.format(" %6.2f%%", nuparams).toString();
                        
                        int firstyear = rs.getInt(21);
                        if (!rs.wasNull()) {
                            params = new Object[]{"RLR DATA FOR  :", firstyear, rs.getString(22)};
                            rowStr = rowFormatter.format("%38s %4d-%4s%n", params).toString();
                            params = new Object[]{"RLR COMPLETENESS  :", rs.getFloat(23)*100.0};
                            rowStr = rowFormatter.format("%38s %6.2f %%%n", params).toString();
                            nuparams = new Object[]{firstyear, rs.getString(22), rs.getFloat(23)*100.0};
                            nurowStr = nurowFormatter.format("   %4d-%4s %6.2f%%",nuparams).toString();
                        }

                        contents.append(rowStr + "\n");
                        catalogueFile.write(contents.toString());
                        nucontents.append(nurowStr + "\n");
                        nucatFile.write(nucontents.toString());
                    }

                }
        } catch (Exception e) {
            System.err.println(e.toString());
        } finally {                       // always close the file
	 if (catalogueFile != null) try {
	    catalogueFile.close();
	 } catch (IOException ioe2) {
	    // just ignore it
	 }
         if (nucatFile != null) try {
            nucatFile.close();
	 } catch (IOException ioe2) {
	    // just ignore it
	 }
      } // end try/catch/finally


        //return contents.toString();
    }

    /******************************************************************************
     * setContents
     *
     * Change the contents of text file in its entirety, overwriting any
     * existing text.
     *
     * This style of implementation throws all exceptions to the caller.
     *
     * @param aFile is an existing file which can be written to.
     * @throws IllegalArgumentException if param does not comply.
     * @throws FileNotFoundException if the file does not exist.
     * @throws IOException if problem encountered during write.
     ****************************************************************************/
    static public void setContents(File aFile, String aContents)
            throws FileNotFoundException, IOException {
        if (aFile == null) {
            throw new IllegalArgumentException("File should not be null.");
        }

        aFile.createNewFile();
        if (!aFile.exists()) {
            throw new FileNotFoundException("File does not exist: " + aFile);
        }
        if (!aFile.isFile()) {
            throw new IllegalArgumentException("Should not be a directory: "
                    + aFile);
        }
        if (!aFile.canWrite()) {
            throw new IllegalArgumentException("File cannot be written: " + aFile);
        }

        //use buffering
        Writer output = new BufferedWriter(new FileWriter(aFile));
        try {
            //FileWriter always assumes default encoding is OK!
            output.write(aContents);
        } finally {
            output.close();
        }
    }

    /******************************************************************************
     * deleteFiles
     *
     ****************************************************************************/
    static public void deleteFiles(String dirPath, final String pattern)
            throws FileNotFoundException, IOException {

        File dirFile = new File(dirPath);

        if (dirPath == null) {
            throw new IllegalArgumentException("dirPath should not be null.");
        }

        try {
            int i;
            int listLength;

            // Get list of files in directory beginning with a numeral
            File[] fileList = dirFile.listFiles(new FilenameFilter() {

                public boolean accept(File dir, String name) {
                    return name.matches(pattern);
                }
            });

            listLength = fileList.length;
            for (i = 1; i <= listLength; i++) {
//                System.out.println("Deleting: " + fileList[i-1].toString());
                fileList[i - 1].delete();
            }

        } catch (Exception e) {
            System.err.println(e.toString());
        }
    }

    /******************************************************
     *
     * Create station pages
     *
     ******************************************************/
    static private void stationPages(java.sql.ResultSet rs)
            throws Exception {

        int rowCount = 0;

        try {
// The data
            while (rs.next()) {
                StringBuilder contents = new StringBuilder();
                Object[] params;

                rowCount++;
                String idStr = rs.getString(2);
                Date dateStamp = rs.getDate(6);
                String dateStr = "";
                if (rs.wasNull()){
                    dateStr = "01 Jan 1980";
                } else {
                    params = new Object[]{dateStamp, dateStamp, dateStamp};
                    dateStr = String.format("%td %tb %tY", params);
                }

                String rlrfirstyear = rs.getString(21);
                String metricfirstyear = rs.getString(25);
                // Only write a station page at all if there is some data
                // i.e. metricfirstyear is not null
                if (!rs.wasNull()) {
                    /**
                    1 NAME, 2 ID, 3 LATITUDE,
                    4 LONGITUDE, 5 GLOSSID,
                    6 DATESTAMP, 7 COASTLINE, 8 STATIONCODE,
                    9 DOCUMENTATION, 10 SUPPLIERID,
                    11 ISRLR, 12 TEXT3, 13 COUNTRY,
                    14 SUPPLIER, 15 ADDRESS1, 16 ADDRESS2,
                    17 ADDRESS3, 18 ADDRESS4, 19 ADDRESS5, 20 ADDRESS6,
                    21 FIRSTYEAR, 22 LASTYEAR, 23 COMPLETENESS,
                    24 RLRDIAGRAMTEXT, 25 METRICFIRSTYEAR, 26 METRICLASTYEAR,
                    27 METRICCOMPLETENESS, 28 FREQUENCY, 29 QCFLAG
                     **/
// Set the RLR diagram flag if RLRDIAGRAMTEXT is not null
                    Integer rlrFlag = Integer.valueOf(0);
                    String diaText = rs.getString(24);
                    if (rs.wasNull()) {
                        rlrFlag = Integer.valueOf(0);
                    } else {
                    // Some stations have text in the rlrdiagramtext but are not actually rlr stations
                        if (rs.getString(11).equals("Y")){
                            rlrFlag = Integer.valueOf(1);
                        } else {
                            rlrFlag = Integer.valueOf(0);
                        }
                    }
                    
                    contents.append("<?php\n"
                            + "$station_id=" + idStr + ";\n"
                            + "$station_name=\"" + rs.getString(1) + "\";\n"
                            + "$lat=" + rs.getString(3) + ";\n"
                            + "$lon=" + rs.getString(4) + ";\n"
                            + "$iso_code3=\"" + rs.getString(12) + "\";\n"
                            + "$country_name=\"" + rs.getString(13) + "\";\n"
                            + "$gloss_id=" + rs.getString(5) + ";\n"
                            + "$coastline=" + rs.getString(7) + ";\n"
                            + "$station=" + rs.getString(8) + ";\n"
                            + "$supplier_id=" + rs.getString(10) + ";\n"
                            + "$isrlr=\"" + rs.getString(11) + "\";\n"
                            + "$rlr_diagram=\"" + rlrFlag.toString() + "\";\n"
                            + "$supplier=\"" + rs.getString(14) + "\";\n"
                            + "$supplier_address1=\"" + rs.getString(15) + "\";\n"
                            + "$supplier_address2=\"" + rs.getString(16) + "\";\n"
                            + "$supplier_address3=\"" + rs.getString(17) + "\";\n"
                            + "$supplier_address4=\"" + rs.getString(18) + "\";\n"
                            + "$supplier_address5=\"" + rs.getString(19) + "\";\n"
                            + "$supplier_address6=\"" + rs.getString(20) + "\";\n");

// RLR station
                    if (rs.getString(11).equals("Y")) {
                        contents.append("$firstyr=\"" + rlrfirstyear + "\";\n"
                                + "$lastyr=\"" + rs.getString(22) + "\";\n"
                                + "$completeness=\"" + rs.getString(23) + "\";\n");
                    } else {
// Metric station - use metric completeness etc
                        contents.append("$firstyr=\"" + metricfirstyear + "\";\n"
                                + "$lastyr=\"" + rs.getString(26) + "\";\n"
                                + "$completeness=\"" + rs.getString(27) + "\";\n");
                    }

                    contents.append("$fcode=\"" + rs.getString(28) + "\";\n"
                            + "$qcflag=\"" + rs.getString(29) + "\";\n"
                            + "$last_update=\"" + dateStr + "\";\n"
                            + "include \"map_page.php\"\n"
                            + "?>\n");

// Write out the file
                    String path = base_path + "stations/";
                    File outFile = new File(path + idStr + ".php");
                    setContents(outFile, contents.toString());

                }
            }
//        System.out.println("RowCount: " + Integer.valueOf(rowCount).toString());
        } catch (Exception e) {
            System.err.println(e.toString());
        }

        return;
    }

    /******************************************************
     *
     * Create Conversion page
     * 
     * Added MET 21/4/2010
     *
     ******************************************************/
    static private void conversionPage(java.sql.ResultSet rs)
            throws Exception {

        /**
        1 NAME, 2 ID, 3 LATITUDE,
        4 LONGITUDE, 5 GLOSSID,
        6 DATESTAMP, 7 COASTLINE, 8 STATIONCODE,
        9 DOCUMENTATION, 10 SUPPLIERID,
        11 ISRLR, 12 TEXT3, 13 COUNTRY,
        14 SUPPLIER, 15 ADDRESS1, 16 ADDRESS2,
        17 ADDRESS3, 18 ADDRESS4, 19 ADDRESS5, 20 ADDRESS6,
        21 FIRSTYEAR, 22 LASTYEAR, 23 COMPLETENESS,
        24 RLRDIAGRAMTEXT, 25 METRICFIRSTYEAR, 26 METRICLASTYEAR,
        27 METRICCOMPLETENESS, 28 FREQUENCY, 29 QCFLAG
         **/
        
        FileWriter conversionFile = new FileWriter(base_path + "conversion_table.php", false);
        
        try {
// Print table
            StringBuilder contents = new StringBuilder();
// Table header
            contents.append("CCode\tSCode\tStnID\n");
            Object[] params;
            String coastStr;
            String stnStr;

            while (rs.next()) {

                String idStr = rs.getString(2);

                Integer coast = Integer.valueOf(rs.getInt(7));
                params = new Object[]{coast};
                coastStr = String.format("%03d", params);

                Integer station = Integer.valueOf(rs.getInt(8));
                params = new Object[]{station};
                stnStr = String.format("%03d", params);

                contents.append(coastStr + "\t" +
                        stnStr + "\t" + idStr + "\t\n");
            }
            conversionFile.write(contents.toString());
        }catch (Exception e) {
            System.err.println(e.toString());
        } finally {                       // always close the file
	    if (conversionFile != null) try {
                 conversionFile.close();
            } catch (IOException ioe2) {
                // just ignore it
            }
        }
    }
     /******************************************************
     *
     * Create documentation pages
     *
     ******************************************************/
    static private void docuPages(java.sql.ResultSet rs)
            throws Exception {

        /**
        1 NAME, 2 ID, 3 LATITUDE,
        4 LONGITUDE, 5 GLOSSID,
        6 DATESTAMP, 7 COASTLINE, 8 STATIONCODE,
        9 DOCUMENTATION, 10 SUPPLIERID,
        11 ISRLR, 12 TEXT3, 13 COUNTRY,
        14 SUPPLIER, 15 ADDRESS1, 16 ADDRESS2,
        17 ADDRESS3, 18 ADDRESS4, 19 ADDRESS5, 20 ADDRESS6,
        21 FIRSTYEAR, 22 LASTYEAR, 23 COMPLETENESS,
        24 RLRDIAGRAMTEXT, 25 METRICFIRSTYEAR, 26 METRICLASTYEAR,
        27 METRICCOMPLETENESS, 28 FREQUENCY, 29 QCFLAG
         **/
        try {
            /*// Count rows
            rs.last();
            int rowcnt = rs.getRow();
            System.out.println("Total rows: " + Integer.valueOf(rowcnt).toString());*/
// The data
            while (rs.next()) {
                StringBuilder contents = new StringBuilder();
                String stnName = rs.getString(1);
                String idStr = rs.getString(2);
                Clob docClob = rs.getClob(9);
                int rlrFlag = 0;
                String diaStr = new String();

                String diaText = rs.getString(24);
                //System.out.println(idStr+": "+diaText);
                if (rs.wasNull()) {                            
                    rlrFlag = 0;
                    //System.out.println(idStr+": Null: "+Integer.valueOf(rlrFlag).toString());
                } else {
                    // Some stations have text in the rlrdiagramtext but are not actually rlr stations
                    if (rs.getString(11).equals("Y")){
                        rlrFlag = 1;
                        diaStr = diaText.replaceAll("\\n", "<br>");
                    } else {
                        rlrFlag = 0;
                    }
                }

                if (docClob != null) {
                    long clobLen = docClob.length();
                    contents.append(docClob.getSubString(1, (int) clobLen) + "\n");
                }

// Write out the file
                String path = new String();
                String isrlr = rs.getString(11);
// Is it RLR?
                if (isrlr.equals("Y")) {
                    // Yes: write file to rlr and metric directories
                    // Should we write out an RLR diagram?
                    if (rlrFlag == 1) {
                        // Write out the RLR  diagram text
                        rlrText(diaStr, idStr);
                        // Produce RLR diagram
                        rlrPage(stnName, idStr);
                    }
                }

                    // Write rlr docs
                    path = base_path + "docu.psmsl/";
                    File outFile = new File(path + idStr + ".txt");
                    String strContents = contents.toString();

                    if (docClob != null) {
                        setContents(outFile, strContents);
                    }

                    // Write docs
                    path = base_path + "docu.psmsl/php/";
                    strContents = strContents.replaceAll("\\n", "<br>");
                    outFile = new File(path + idStr + ".php");
                    if (docClob != null) {
                        setContents(outFile, strContents);
                    }

                /*} else {
                // No: Only write to metric directory
                path = base_path + "docu.psmsl/";
                File outFile = new File(path + idStr + ".php");
                String strContents = contents.toString();
                strContents = strContents.replaceAll("\\n", "<br>");
                if (docClob != null) {
                setContents(outFile, strContents);
                }

                path = base_path + "docu.psmsl/php/";
                File outFile = new File(path + idStr + ".php");
                String strContents = contents.toString();
                strContents = strContents.replaceAll("\\n", "<br>");
                if (docClob != null) {
                setContents(outFile, strContents);
                }
                }*/
            }
        } catch (Exception e) {
            System.err.println(e.toString());
        }

        return;
    }

    /******************************************************
     *
     * Create authority documentation pages
     *
     ******************************************************/
    static private void authPages(java.sql.Connection con, java.sql.ResultSet rs)
            throws Exception {

        /**
        1 NAME, 2 ID, 3 DOCUMENTATION
         **/
        ResultSet auth_rs = null;
        PreparedStatement auth_ps = null;
        try {
            /*// Count rows
            rs.last();
            int rowcnt = rs.getRow();
            System.out.println("Total rows: " + Integer.valueOf(rowcnt).toString());*/
            auth_ps = getAuthStatement(con);

// The data
            while (rs.next()) {
                StringBuilder contents = new StringBuilder();
                String idStr = rs.getString(2);
                //System.out.println("ID: " + idStr);

                // Remember to use the supplierid from the stationsummary as the key here...
                auth_ps.setInt(1, rs.getInt(10));
                //System.out.println("set Int: ");
                auth_rs = auth_ps.executeQuery();
                // We're not incrementing with next as we only have 1 row in the resultset
                // Use an absolute determination of the row
                auth_rs.absolute(1);
                //System.out.println("executed query: ");

                Clob docClob = auth_rs.getClob(3);
                //System.out.println("got clob: ");

                if (docClob != null) {
                    long clobLen = docClob.length();
                    contents.append(docClob.getSubString(1, (int) clobLen) + "\n");
                }

                auth_rs.close();
// Write out the file
                String path = new String();
                String isrlr = rs.getString(11);
// Is it RLR?
                if (isrlr.equals("Y")) {
                    // Yes: write file to rlr and metric directories

                    // Write docs
                    path = base_path + "docu.psmsl/";
                    File outFile = new File(path + idStr + "_auth.txt");
                    String strContents = contents.toString();
                    
                    if (docClob != null) {
                        setContents(outFile, strContents);
                    }

                    // Write docs without line breaks
                    path = base_path + "docu.psmsl/php/";
                    strContents = strContents.replaceAll("\\n", "<br>");
                    outFile = new File(path + idStr + "_auth.php");
                    if (docClob != null) {
                        setContents(outFile, strContents);
                    }
                }

                    /*} else {
                    // No: Only write to metric directory

                    path = base_path + "docu.psmsl/";

                    File outFile = new File(path + idStr + "_auth.txt");
                    String strContents = contents.toString();
                    strContents = strContents.replaceAll("\\n", "<br>");
                    if (docClob != null) {
                    setContents(outFile, strContents);
                    }
                    }*/
            }
        } catch (Exception e) {
            System.err.println(e.toString());
        }

        auth_ps.close();

        return;
    }

    /******************************************************
     *
     * Create catalogue pages for zip files. One each for
     * RLR annual, RLR monthly and metric monthly directories
     *
     ******************************************************/
    static private void fileLists(java.sql.ResultSet rs, Hashtable zeroIds)
            throws Exception {

        /**
        1 NAME, 2 ID, 3 LATITUDE,
        4 LONGITUDE, 5 GLOSSID,
        6 DATESTAMP, 7 COASTLINE, 8 STATIONCODE,
        9 DOCUMENTATION, 10 SUPPLIERID,
        11 ISRLR, 12 TEXT3, 13 COUNTRY,
        14 SUPPLIER, 15 ADDRESS1, 16 ADDRESS2,
        17 ADDRESS3, 18 ADDRESS4, 19 ADDRESS5, 20 ADDRESS6,
        21 FIRSTYEAR, 22 LASTYEAR, 23 COMPLETENESS,
        24 RLRDIAGRAMTEXT, 25 METRICFIRSTYEAR, 26 METRICLASTYEAR,
        27 METRICCOMPLETENESS, 28 FREQUENCY, 29 QCFLAG
         **/
        try {

            String path = new String();
            StringBuilder rlra_contents = new StringBuilder();
            StringBuilder rlrm_contents = new StringBuilder();
            StringBuilder met_contents = new StringBuilder();

            // The data
            while (rs.next()) {
                StringBuilder rowSb = new StringBuilder();
                java.util.Formatter rowFormatter = new java.util.Formatter(rowSb, java.util.Locale.US);
                Object[] params;

                String rlrStr = rs.getString(11);
                String idStr = rs.getString(2);
                String rowStr = new String();

                // Format of file is: id, lat, lon, name, ccode,scode, qcflag
                params = new Object[]{rs.getString(2), rs.getFloat(3),
                            rs.getFloat(4), rs.getString(1), rs.getInt(7),
                            rs.getInt(8), rs.getString(29)};
                rowStr = rowFormatter.format("%5s; % 10.6f; % 11.6f; %-40s; %03d; %03d; %1s", params).toString();

                /*
                 * Check the hashtable for the idStr.
                 * Convert zeroIdStr string to a character array and check each
                 * character.
                 * Don't add the row to the relevant fileList if the character
                 * is set to 1.
                 * The order of the character array is:
                 * rlr monthly
                 * rlr annual
                 * metric monthly
                 */

                String zeroIdStr = zeroIds.get(idStr).toString();
                char[] zeroIdChar = zeroIdStr.toCharArray();

                // Is it RLR?
                if (rlrStr.equals("Y")) {
                    // Yes: write file to rlr and metric files
                    // Check if idStr is matched in the zeroIds

                    if (zeroIdChar[0] == '0') {
                        /*System.out.println("Matched " + idStr + "A");
                        } else {*/
                        rlrm_contents.append(rowStr + "\n");
                    }

                    if (zeroIdChar[1] == '0') {
                        /*System.out.println("Matched " + idStr + "R");
                        } else {*/
                        rlra_contents.append(rowStr + "\n");
                    }

                    if (zeroIdChar[2] == '0') {
                        /*System.out.println("Matched " + idStr + "M");
                        } else {*/
                        met_contents.append(rowStr + "\n");
                    }
                } else {
                    // No: Only write to metric file

                    if (zeroIdChar[2] == '0') {
                        met_contents.append(rowStr + "\n");
                    }
                }

            }
            // Write rlr zip file catalogues
            path = base_path + "rlr.annual.data/";
            File outFile = new File(path + "filelist.txt");
            String strContents = rlra_contents.toString();

            setContents(outFile, strContents);
            path = base_path + "rlr.monthly.data/";
            outFile = new File(path + "filelist.txt");
            strContents = rlrm_contents.toString();

            setContents(outFile, strContents);
            // Write metric zip file catalogue
            path = base_path + "met.monthly.data/";
            strContents = met_contents.toString();
            outFile = new File(path + "filelist.txt");

            setContents(outFile, strContents);
        } catch (Exception e) {
            System.err.println(e.toString());
        }
        return;
    }

    /******************************************************
     *
     * Create RLR diagrams
     *
     ******************************************************/
    static private void rlrPage(String stnName, String idStr)
            throws Exception {

        try {
// The data

            StringBuilder contents = new StringBuilder();

            contents.append("<?php $station_id=" + idStr
                    + ";\n $station_name=\"" + stnName + "\";\n"
                    + "require(\"diagram_page.php\");\n ?>\n");

// Write out the file
            String path = base_path + "rlr.diagrams/";

            File outFile = new File(path + idStr + ".php");
            setContents(
                    outFile, contents.toString());

        } catch (Exception e) {
            System.err.println(e.toString());
        }

        return;

    }

    /******************************************************
     *
     * Produce RLR text files
     *
     ******************************************************/
    static private void rlrText(String diaText, String idStr)
            throws Exception {

        try {
// Write out the file

            String path = base_path + "rlr.diagrams/text/";

            File outFile = new File(path + idStr + ".txt");
            setContents(
                    outFile, diaText);

        } catch (Exception e) {
            System.err.println(e.toString());
        }

        return;

    }

    /******************************************************
     *
     * Get prepared statement for a station
     *
     ******************************************************/
    public static PreparedStatement getDataStatement(Connection con, String type, String frequency, String idStr)
            throws Exception {

// Declare the JDBC objects.
        PreparedStatement ps = null;

        try {
            // Create a Statement object so we can submit SQL
            // statements to the driver

            if (frequency.equals("annual")) {

                ps = con.prepareStatement(
                    "SELECT YEAR, " + type + ", MISSING, QCFLAG "
                    + "FROM " + frequency
                    + " WHERE ID=" + idStr + " AND ISONLINE='Y' "
                    + "ORDER BY YEAR",
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);

            } else if (frequency.equals("monthly")) {

                ps = con.prepareStatement(
                    "SELECT TIME, " + type + ", MISSING, QCFLAG, INTERPOLATED "
                    + "FROM " + frequency
                    + " WHERE ID=" + idStr + " AND ISONLINE='Y' "
                    + "ORDER BY TIME",
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);

            } else {
                throw new Exception("period undefined ");

            } // Only return data that is going on-line


        } catch (Exception e) {
            e.printStackTrace();
        }

        return (ps);

    }

    /******************************************************
     *
     * Get data resultset for a station
     *
     ******************************************************/
    public static ResultSet getData(PreparedStatement ps)
            throws Exception {

// Declare the JDBC objects.
        ResultSet rs = null;

        try {
// Submit a query, creating a ResultSet object
            rs = ps.executeQuery();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return (rs);

    }

    /******************************************************************************
     *
     * Get table statement
     *
     *****************************************************************************/
    public static PreparedStatement getTableStatement(Connection con)
            throws Exception {

// Declare the JDBC objects.
        PreparedStatement ps = null;

        try {
// Create a Statement object so we can submit SQL
// statements to the driver
            ps = con.prepareStatement(
                    "SELECT NAME, ID, LATITUDE, "
                    + "LONGITUDE, GLOSSID, "
                    + "LASTUPDATED, COASTLINE, STATIONCODE, "
                    + "DOCUMENTATION, SUPPLIERID, "
                    + "ISRLR, TEXT3, COUNTRY, SUPPLIER, ADDRESS1, ADDRESS2, "
                    + "ADDRESS3, ADDRESS4, ADDRESS5, ADDRESS6, "
                    + "FIRSTYEAR, LASTYEAR, COMPLETENESS, RLRDIAGRAMTEXT, "
                    + "METRICFIRSTYEAR, METRICLASTYEAR, METRICCOMPLETENESS, " 
                    + "FREQUENCY, QCFLAG "
                    + "FROM STATIONSUMMARY "
                    + "ORDER BY COASTLINE, STATIONCODE",
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return (ps);

    }

    /******************************************************************************
     *
     * Get authority documentation statement
     *
     *****************************************************************************/
    public static PreparedStatement getAuthStatement(Connection con)
            throws Exception {

// Declare the JDBC objects.
        PreparedStatement ps = null;

        try {
// Create a Statement object so we can submit SQL
// statements to the driver
            ps = con.prepareStatement(
                    "SELECT NAME, ID, DOCUMENTATION " +
                    "FROM ADDRESS " +
                    "WHERE ID=?",
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return (ps);

    }

    /******************************************************
     *
     * Get connection to the database
     *
     ******************************************************/
    public static Connection getConnection(String dbase)
            throws Exception {

// Declare the JDBC objects.
        Connection con = null;

        try {
            char password[] = null;



            if (dbase.equals("LID")) {
                try {
                    password = PasswordField.getPassword(System.in, "Enter your password: ");

                } catch (IOException ioe) {
                    ioe.printStackTrace();

                }
                Class.forName("oracle.jdbc.driver.OracleDriver");
                con = DriverManager.getConnection(
                        "jdbc:oracle:thin:@example:1526:AAA",
                        "test",
                        String.valueOf(password));


            } else if (dbase.equals("AAA")) {
                Class.forName("oracle.jdbc.driver.OracleDriver");
                con = DriverManager.getConnection(
                        "jdbc:oracle:thin:@dbase@example.com":1526:AAA",
                        "guest",
                        "user");
                /*if (!con.isClosed()){
                System.out.println("Connection is valid");
                }*/
//            String.valueOf(password));

            } else {
                throw new Exception("Database name unknown ");

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return (con);

    }

    /*******************************************************************************
     * Produce data pages
     ******************************************************************************/
    private static Hashtable dataPages(ResultSet rs, Connection con)
            throws Exception {
        ResultSet data_rs = null;
        PreparedStatement data_ps = null;

        Hashtable<String, String> zeroIds = new Hashtable<String, String>();

        int rowCount = 0;

        try {
// Get the data
            while (rs.next()) {
                rowCount++;
                String idStr = rs.getString(2);
                String isRLR = rs.getString(11);

                boolean zeroId;
                /*
                We need to return a set of values to determine whether any of
                the 3 potential data files for this station are empty.
                '1' means the file is empty and the order of the characters
                is rlr monthly, rlr annual and met monthly.
                 */
                char[] zeroIdChar = {'0', '0', '0'};

                // Get RLR monthly data
                if (isRLR.equals("Y")) {
                    data_ps = getDataStatement(con, "rlrdata", "monthly", idStr);
                    /* Monthly data returns resultset:
                    1 TIME, 2 RLRDATA 3 MISSING 4 QCFLAG 5 INTERPOLATED*/
                    data_rs = getData(data_ps);

                    // Write data pages
                    // zeroId returns true if the data file is empty
                    zeroId = writeData(data_rs, "rlr", "monthly", idStr);

                    if (zeroId) {
                        // Set to 1 if empty for RLR monthly ids
                        zeroIdChar[0] = '1';
                    }
                    data_ps.close();
                    data_rs.close();

                    // Get RLR annual data
                    data_ps = getDataStatement(con, "rlrdata", "annual", idStr);
                    /* ANNUAL data returns resultset:
                    1 YEAR, 2 RLRDATA 3 MISSING 4 QCFLAG*/
                    data_rs = getData(data_ps);

                    // Write data pages
                    zeroId = writeData(data_rs, "rlr", "annual", idStr);

                    if (zeroId) {
                        // Set to 1 if empty for RLR annual ids
                        zeroIdChar[1] = '1';
                    }
                    data_ps.close();
                    data_rs.close();
                }

                // Get metric monthly data
                data_ps = getDataStatement(con, "metricdata", "monthly", idStr);
                /* Metric monthly data returns resultset:
                    1 TIME, 2 METRICDATA 3 MISSING 4 QCFLAG 5 INTERPOLATED*/
                data_rs = getData(data_ps);

                // Write data pages
                zeroId = writeData(data_rs, "met", "monthly", idStr);

                if (zeroId) {
                    // Set to 1 if empty for metric monthly ids
                    zeroIdChar[2] = '1';
                }
                data_ps.close();
                data_rs.close();

                // Turn the character array into a string
                String zeroIdStr = String.valueOf(zeroIdChar);

                // Add the "zeros" string to the hashtable with the idStr as the key
                zeroIds.put(idStr, zeroIdStr);
            }

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
// Close the result set
            if (data_rs != null) {
                try {
                    data_rs.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (data_ps != null) {
                try {
                    data_ps.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return zeroIds;
    }

    /******************************************************
     *
     * Produce data files
     *
     ******************************************************/
    static private boolean writeData(ResultSet rs, String type, String frequency, String idStr)
            throws Exception {

        StringBuilder contents = new StringBuilder();

        boolean zeroId = false;
        java.util.Date thisDate = null;
        java.util.Date nextDate = null;

        try {
            while (rs.next()) {
                String rowStr = new String();
                StringBuilder rowSb = new StringBuilder();                
                java.util.Formatter rowFormatter = new java.util.Formatter(rowSb, java.util.Locale.US);
                String dateStr = new String();                

                // Set flag to see if we have started the real data
                // rather than null rows
                boolean dataStart = false;

                int monInt;
                double monFlo;

                float yrFlo;
                String flagStr = new String();
                String heightStr = new String();
                Object[] params;
                boolean writeRows = false;
                String dateStamp = new String();

                // DATESTAMP
                if (frequency.equals("monthly")) {
                    dateStamp = rs.getDate(1).toString();
                    dateStr = getMonthlyDateString(dateStamp);
                } else if (frequency.equals("annual")) {
                    dateStr = rs.getString(1);
                } else {
                    throw new Exception("Date frequency not found ");
                }

                // Set flags to octal form
                if (rs.getString(4).equals("N")) {
                    flagStr = "000";

                } else if (rs.getString(4).equals("Y")) {
                    flagStr = "001";

                } else {
                    throw new Exception("Flag not found ");

                }

                // Get height string
                heightStr = rs.getString(2);

                if (!rs.wasNull()) {
                    writeRows = true;

                    // Set the dataStart flag to true if it is false
                    if (dataStart == false){
                        dataStart = true;
                    }
                }
                

                if (dataStart) {
                    if (frequency.equals("annual")) {

                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
                        thisDate = sdf.parse(dateStr);

                        if(nextDate!=null){
                            // Check to see whether the date read in "thisDate"
                            // is after what it should be i.e. "nextDate"
                            while(nextDate.before(thisDate)){
                                
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(nextDate);
                                String yearStr = Integer.valueOf(cal.get(Calendar.YEAR)).toString();
                                String padStr = annualPadString(yearStr);
                                contents.append(padStr + "\n");

                                cal.add(Calendar.YEAR, 1);
                                nextDate = cal.getTime();
                            }
                        }
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(thisDate);
                        cal.add(Calendar.YEAR, 1);
                        nextDate = cal.getTime();
                    } else {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        thisDate = sdf.parse(dateStamp);

                        if(nextDate!=null){
                            // Check to see whether the date read in "thisDate"
                            // is after what it should be i.e. "nextDate"
                            while(nextDate.before(thisDate)){
                                
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(nextDate);
                                yrFlo = Integer.valueOf(cal.get(Calendar.YEAR)).floatValue();
                                monInt = Integer.valueOf(cal.get(Calendar.MONTH));

                                // Months have a base of zero here so no need to subtract 1.0
                                monFlo = (monInt / 12.0) + 1.0 / 24.0;
                                yrFlo = yrFlo + Double.valueOf(monFlo).floatValue();

                                String padStr=monthlyPadString(yrFlo);
                                contents.append(padStr + "\n");

                                cal.add(Calendar.MONTH, 1);
                                nextDate = cal.getTime();
                            }
                        }

                        Calendar cal = Calendar.getInstance();
                        cal.setTime(thisDate);
                        cal.add(Calendar.MONTH, 1);
                        nextDate = cal.getTime();
                    }
                }


                if (writeRows) {
                    
                    if (frequency.equals("annual")) {
                        params = new Object[]{dateStr, heightStr, rs.getString(3), flagStr};
                        rowStr = rowFormatter.format("%5s;%6s;%s;%s", params).toString();
                    } else {
                        // Is data interpolated? If yes, set missing days to 99
                        if (rs.getString(5).equals("Y")){
                           params = new Object[]{dateStr, heightStr, "99", flagStr}; 
                        } else {
                          params = new Object[]{dateStr, heightStr, rs.getString(3), flagStr};
                        }
                        rowStr = rowFormatter.format("%11s;%6s;%2s;%s", params).toString();
                    }
                    contents.append(rowStr + "\n");
                }

            }

            // Check for zero length files
            if (contents.toString().length() != 0) {
// Write out the file
                String path = base_path + type + "." + frequency + ".data/";

                File outFile = new File(path + idStr + "." + type + "data");
                setContents(
                        outFile, contents.toString());
            } else {
                zeroId = true;
            }

        } catch (Exception e) {
            System.err.println(e.toString());
        }

        return zeroId;

    }

        /******************************************************
         *
         * Get date string
         *
         *****************************************************/

    static private String getMonthlyDateString(String dateStamp)
            throws Exception {
        String dateStr = new String();
        StringBuilder dateSb = new StringBuilder();
        java.util.Formatter dateFormatter = new java.util.Formatter(dateSb, java.util.Locale.US);
        String[] splitStr = new String[3];

        splitStr = dateStamp.split("-");
        Float yrFlo = Float.parseFloat(splitStr[0]);
        int monInt = Integer.parseInt(splitStr[1]);
        Double monFlo = ((monInt - 1.0) / 12.0) + 1.0 / 24.0;
        yrFlo = yrFlo + Double.valueOf(monFlo).floatValue();
        dateFormatter = dateFormatter.format("%11.4f", Float.valueOf(yrFlo));
        dateStr = dateFormatter.toString();

        return dateStr;
    }

    /******************************************************
     *
     * Get the list of incomplete years for catalogue files
     * What we want to do is iterate over the data and record
     * the years where there are missing months in an integer array
     * To limit the size of the integer array that is declared
     * one of the arguments passed to this method is an integer which
     * is the maximum number of missing years based on the length
     * of the data and the completeness
     *
     ******************************************************/
    /*static private int[] getIncompleteYears(ResultSet rs, int i)
    throws Exception {

    int [] missingYears = new int[i];
    int count=0;

    java.util.Date thisDate = null;
    java.util.Date nextDate = null;

    try {

    while (rs.next()) {
    int year;
    String dateStamp = new String();

    // DATESTAMP
    dateStamp = rs.getDate(1).toString();

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    thisDate = sdf.parse(dateStamp);

    if (nextDate != null) {
    // Check to see whether the date read in "thisDate"
    // is after what it should be i.e. "nextDate"
    while (nextDate.before(thisDate)) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(nextDate);
    year = Integer.valueOf(cal.get(Calendar.YEAR));
    // Check whether we have gone over a year boundary
    if (missingYears[count]==0){
    missingYears[count] = year;
    } else if (missingYears[count] == (year+1)){
    count++;
    missingYears[count] = year;
    } else {
    missingYears[count] = year;
    }

    cal.add(Calendar.MONTH, 1);
    nextDate = cal.getTime();
    }
    }

    Calendar cal = Calendar.getInstance();
    cal.setTime(thisDate);
    cal.add(Calendar.MONTH, 1);
    nextDate = cal.getTime();

    count++;
    }

    } catch (Exception e) {
    System.err.println(e.toString());
    }

    return missingYears;
    }*/

}
