package app.univtool.model;

public class Course {
    public Integer id;
    public String name;
    public String day;
    public String period;
    public String code;
    public Integer grade;
    public String kind;
    public Double credit;
    public String teacher;
    public String email;
    public String webpage;
    public String syllabus;
    public boolean attendanceRequired;
    public String examsCsv;
    public Integer year;
    public String term;
    public String folder;


    @Override public String toString() { return name; }
}
