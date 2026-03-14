package app.univtool.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import app.univtool.core.Database;
import app.univtool.model.Course;

public class CourseRepository {

	public int insert(Course c) {
	    String sql = """
	        INSERT INTO course
	          (name, day, period, code, grade, kind, credit, teacher, email, webpage, syllabus,
	           attendance_required, exams, year, term, folder)
	        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)   -- folderは一旦NULLでもOK
	    """;
	    try (Connection con = Database.get();
	         PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
	        int i = 1;
	        ps.setString(i++, c.name);
	        ps.setString(i++, c.day);
	        ps.setString(i++, c.period);
	        ps.setString(i++, c.code);
	        if (c.grade == null) ps.setNull(i++, java.sql.Types.INTEGER); else ps.setInt(i++, c.grade);
	        ps.setString(i++, c.kind);
	        if (c.credit == null) ps.setNull(i++, java.sql.Types.REAL); else ps.setDouble(i++, c.credit);
	        ps.setString(i++, c.teacher);
	        ps.setString(i++, c.email);
	        ps.setString(i++, c.webpage);
	        ps.setString(i++, c.syllabus);
	        ps.setInt(i++, c.attendanceRequired ? 1 : 0);
	        ps.setString(i++, c.examsCsv);
	        if (c.year == null) ps.setNull(i++, java.sql.Types.INTEGER); else ps.setInt(i++, c.year);
	        ps.setString(i++, c.term);
	        ps.setString(i++, c.folder);

	        ps.executeUpdate();
	        try (ResultSet keys = ps.getGeneratedKeys()) {
	            if (keys.next()) return keys.getInt(1);
	        }
	        try (var st = con.createStatement();
	             var rs = st.executeQuery("SELECT last_insert_rowid()")) {
	            if (rs.next()) return rs.getInt(1);
	        }
	        throw new RuntimeException("生成IDの取得に失敗");
	    } catch (SQLException e) {
	        throw new RuntimeException(e);
	    }
	}
	
	public void updateFolder(int id, String folder) {
	    String sql = "UPDATE course SET folder=? WHERE id=?";
	    try (Connection con = Database.get();
	         PreparedStatement ps = con.prepareStatement(sql)) {
	        ps.setString(1, folder);
	        ps.setInt(2, id);
	        ps.executeUpdate();
	    } catch (SQLException e) {
	        throw new RuntimeException(e);
	    }
	}



    public List<Course> findAll() {
    	String sql = "SELECT id,folder,name,day,period,code,grade,kind,credit,teacher,email,webpage,syllabus,attendance_required,exams,year,term FROM course"; // ★ term 追加
        List<Course> list = new ArrayList<>();
        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Course c = new Course();
                c.id = rs.getInt("id");
                c.folder = rs.getString("folder");
                c.name = rs.getString("name");
                c.day = rs.getString("day");
                c.period = rs.getString("period");
                c.code = rs.getString("code");
                int g = rs.getInt("grade"); c.grade = rs.wasNull() ? null : g;
                c.kind = rs.getString("kind");
                double cr = rs.getDouble("credit"); c.credit = rs.wasNull() ? null : cr;
                c.teacher = rs.getString("teacher");
                c.email = rs.getString("email");
                c.webpage = rs.getString("webpage");
                c.syllabus = rs.getString("syllabus");
                c.attendanceRequired = rs.getInt("attendance_required") == 1;
                c.examsCsv = rs.getString("exams");
                int y = rs.getInt("year"); c.year = rs.wasNull() ? null : y;
                c.term = rs.getString("term");
                list.add(c);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }
    
    public void update(Course c) {
        String sql = """
            UPDATE course SET
              name=?, day=?, period=?, code=?, grade=?, kind=?, credit=?, teacher=?, email=?, webpage=?, syllabus=?,
              attendance_required=?, exams=?, year=?, term=?         -- ★ term まで更新
            WHERE id=?
        """;
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, c.name);
            ps.setString(i++, c.day);
            ps.setString(i++, c.period);
            ps.setString(i++, c.code);
            if (c.grade == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, c.grade);
            ps.setString(i++, c.kind);
            if (c.credit == null) ps.setNull(i++, Types.REAL); else ps.setDouble(i++, c.credit);
            ps.setString(i++, c.teacher);
            ps.setString(i++, c.email);
            ps.setString(i++, c.webpage);
            ps.setString(i++, c.syllabus);
            ps.setInt(i++, c.attendanceRequired ? 1 : 0);
            ps.setString(i++, c.examsCsv);
            if (c.year == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, c.year);
            ps.setString(i++, c.term);
            ps.setInt(i++, c.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void delete(int id) {
        String sql = "DELETE FROM course WHERE id=?";
        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

