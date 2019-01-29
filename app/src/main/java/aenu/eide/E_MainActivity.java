
//license wtfpl 2.0

//by aenu 2018/10/22
//   email:202983447@qq.com

package aenu.eide;

import aenu.eide.PL.CxxLanguage;
import aenu.eide.diagnostic.DiagnosticCallback;
import aenu.eide.diagnostic.DiagnosticInfo;
import aenu.eide.diagnostic.ProjectDiagnostic;
import aenu.eide.gradle_impl.GradleProject;
import aenu.eide.view.CodeEditor;
import aenu.eide.view.CxxEditor;
import aenu.eide.view.GradleEditor;
import aenu.eide.view.JavaEditor;
import aenu.eide.view.ProjectPage;
import aenu.eide.view.ProjectView;
import aenu.eide.view.XmlEditor;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class E_MainActivity extends AppCompatActivity implements RequestListener, DiagnosticCallback
{
    public static final int REQUEST_OPEN_PROJECT=0x3584;
    public static final int REQUEST_OPEN_FILE=0x3585;
    
    public final int event_click_project_icon=0xaa123456;
    public final int event_open_project=0xaa123457;
    public final int event_open_file=0xaa123458;
    
    private GradleProject mGradleProject;
    private ProjectDiagnostic mProjectDiagnostic;
    private ProjectView project_view;
    
    private final Map<Class,CodeEditor> EDITOR=new HashMap<>();
    private final List<Fragment> backStack=new ArrayList<>();
    private DrawerLayout drawer;
    private ViewPager pager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
             
        this.drawer= (DrawerLayout) findViewById(R.id.drawer);
        this.pager=(ViewPager) findViewById(R.id.project_pager);
        this.pager.setAdapter(new ProjectPage(this,null,null));
        
        initEditor();
        initActionBar();
        
        if(!E_Application.getAppVersion().equals(
          E_Application.getConfigVersion(this))){
            try{
                  E_Installer.install_toolchain(this);
                  E_Installer.install_template_and_key(this);
                  //E_Installer.install_ndk(this,E_Application.getNdkUrl());
                  E_Application.updateConfigVersion(this);
            }
            catch (Exception e) {
                E_Application.clearConfigVersion(this);
                throw new RuntimeException(e);
            }
        }
        
    }
  
    
    private void initEditor(){
        EDITOR.put(JavaEditor.class,new JavaEditor(this,R.style.FreeScrollingTextField_Light));
        EDITOR.put(CxxEditor.class,new CxxEditor(this,R.style.FreeScrollingTextField_Light));  
        EDITOR.put(GradleEditor.class,new GradleEditor(this,R.style.FreeScrollingTextField_Light));  
        EDITOR.put(XmlEditor.class,new XmlEditor(this,R.style.FreeScrollingTextField_Light));         
    }
    
    private void initActionBar(){
        
        Toolbar tBar= (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(tBar);
        
        tBar.setNavigationIcon(R.drawable.ic_p_show);
        tBar.setTag(R.drawable.ic_p_show);
        tBar.setNavigationOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View p1){   
                on_event(event_click_project_icon);
            }
        });
        
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }
    
    private void swapProjectIcon(){
        Toolbar tBar= (Toolbar) findViewById(R.id.toolbar);
        int projectNewI=((int)tBar.getTag())^R.drawable.ic_p_show^R.drawable.ic_p_hide;
        
        tBar.setNavigationIcon(projectNewI);
        tBar.setTag(projectNewI);                     
    }
    
    
    public void on_event(int id,Object... data){
        switch(id){
            case event_click_project_icon:
                if(!drawer.isDrawerOpen(Gravity.LEFT))
                drawer.openDrawer(Gravity.LEFT);
                else
                    drawer.closeDrawer(Gravity.LEFT);
            
                swapProjectIcon();          
                break;
               case event_open_project:
            mGradleProject=GradleProject.open(this,new File(data[0]+"/build.gradle"));
            
            pager.setAdapter(new ProjectPage(this,new File((String)data[0]),this));
            drawer.openDrawer(Gravity.LEFT);
                   break;
            case R.id.menu_file_browser:  
            
              startActivityForResult(new Intent(this,E_FileActivity.class),REQUEST_OPEN_PROJECT);
            
            break;
            case R.id.menu_open_term:
                startActivity(new Intent(this,E_TermActivity.class));
                break;
                
            case R.id.menu_compile:
                if(mGradleProject!=null){
                    
                   codeSave();
                    
                   try{
                        mGradleProject.build(this);
                        Toast.makeText(this,"成功",1).show();                       
                    }catch (Exception e) {    
                        new AlertDialog.Builder(this)
                          .setTitle("编译失败")
                          .setMessage(e.toString())
                          .create()
                          .show();
                    }finally{
                      //  d.dismiss();
                    }
                }break;
            case R.id.menu_undo:
                codeUndo();
                break;
            case R.id.menu_redo:
                codeRedo();
                break;
            default: break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        if(mGradleProject==null)
            menu.findItem(R.id.menu_compile).setEnabled(false);
        else
            menu.findItem(R.id.menu_compile).setEnabled(true);
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        on_event(item.getItemId());
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(resultCode==RESULT_OK){
            switch(requestCode){
                case REQUEST_OPEN_PROJECT:
                    on_event(event_open_project,data.getData().getPath());
                    break;
            }
        }
        
        super.onActivityResult(requestCode, resultCode, data);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
       /* if(backStack.size()!=0){
            Fragment f=backStack.get(backStack.size()-1);
            removeFragmentOnUI(f);
            if(f instanceof Project)
                swapProjectIcon();
            return true;
        }*/
        return super.onKeyDown(keyCode, event);
    }
    
    private CodeEditor getCurrentEditor(){
        ViewGroup code_edit_container=(ViewGroup)findViewById(R.id.code_edit_container);   
        if(code_edit_container.getChildCount()!=0)
            return (CodeEditor) code_edit_container.getChildAt(0);   
        return null;
    }
    
    private void removeCurrentEditor(){
        ViewGroup code_edit_container=(ViewGroup)findViewById(R.id.code_edit_container);   
        if(code_edit_container.getChildCount()!=0)
            code_edit_container.removeViewAt(0);    
    }
    
    private void setCurrentEditor(CodeEditor editor){
        ViewGroup code_edit_container=(ViewGroup)findViewById(R.id.code_edit_container);   
        if(code_edit_container.getChildCount()!=0)
            code_edit_container.removeViewAt(0);           
        code_edit_container.addView(editor);       
    }
    
    private void codeSave(){  
        CodeEditor editor=getCurrentEditor();
        try{
            if(editor!=null) editor.save();
        }catch (IOException e) {}     
    }
    
    private void codeUndo(){
        CodeEditor editor=getCurrentEditor();
        if(editor!=null) editor.undo();     
    }
    
    private void codeRedo(){
        CodeEditor editor=getCurrentEditor();
        if(editor!=null) editor.redo();  
    }
    
    
    private CodeEditor getFileEditor(File file){
        if(file.getName().endsWith(".java"))                                             
            return EDITOR.get(JavaEditor.class);                                    
        else if(file.getName().endsWith(".c")
        ||file.getName().endsWith(".h")
        ||file.getName().endsWith(".cpp")
        ||file.getName().endsWith(".cxx")
        ||file.getName().endsWith(".cc")
        ||file.getName().endsWith(".hpp")
        ||file.getName().endsWith(".hxx")
        ||file.getName().endsWith(".hh")
        )
            return EDITOR.get(CxxEditor.class);
        else if(file.getName().endsWith(".gradle"))
            return EDITOR.get(GradleEditor.class);
        else if(file.getName().endsWith(".xml"))
            return EDITOR.get(XmlEditor.class);
        return null;
    }
    
    private Object[] getFileEditorArgs(File file){
        if(file.getName().endsWith(".c")
        ||file.getName().endsWith(".h"))
            return new Object[] {CxxLanguage.defaultCFlag(),mProjectDiagnostic};    
 
        else if(file.getName().endsWith(".cpp")
        ||file.getName().endsWith(".cxx")
        ||file.getName().endsWith(".cc")
        ||file.getName().endsWith(".hpp")
        ||file.getName().endsWith(".hxx")
        ||file.getName().endsWith(".hh"))
            return new Object[] {CxxLanguage.defaultCFlag(),mProjectDiagnostic};  

        return null;
    }
    
    
    @Override
    public boolean onRequest(int requestCode,Object data){
        switch(requestCode){
            
            case REQUEST_OPEN_FILE:{      
               
                try{
                    CodeEditor editor=getCurrentEditor();
                    if(editor!=null)editor.save();
                }
                catch(IOException e){
                    Toast.makeText(this,"文件保存失败!",1);
                    throw new RuntimeException(e);
                }
                
                try{              
                    CodeEditor editor=getFileEditor((File)data);
                    Object[] args=getFileEditorArgs((File)data);
                    if(editor!=null){
                        setCurrentEditor(editor);
                        editor.read((File)data,args);
                    }
                }catch(IOException e){
                }
            }break;
            }
         /*   case REQUEST_OPEN_PROJECT:{
              
                removeFragmentOnUI(file_browser);
                
                project_tree.setProjectDir((File)data);         
                mGradleProject=GradleProject.open(this,new File((File)data,"build.gradle"));
                mProjectDiagnostic=new CXProjectDiagnostic(mGradleProject,this);
                
                removeFragmentOnUI(project_tree);
                addFragmentOnUI(project_tree);*
                
            }break;
                
        }*/
        return true;
    }
    
    @Override
    public void onDiagStart(){
        
    }

    private List<String> to_string_list(Map<File,List<DiagnosticInfo>> map){
        final ArrayList<String> list=new ArrayList<>();
        final Set<Map.Entry<File,List<DiagnosticInfo>>> entries=map.entrySet();
        if(entries==null)return list;
        for(Map.Entry<File,List<DiagnosticInfo>> e:entries){
            String fn=e.getKey().getAbsolutePath();
            List<DiagnosticInfo> infos=e.getValue();
            for(DiagnosticInfo info:infos){
                list.add(fn+":"+info.line+":"+info.column+":"+info.info);               
            }
        }
        return list;
    }
    
    @Override
    public void onDiagDone(){
       
        printErrors(to_string_list(mProjectDiagnostic.errors));
        printWarnings(to_string_list(mProjectDiagnostic.warnings));
        
       /* project_tree.setErrors(mProjectDiagnostic.errors);
        project_tree.setWarnings(mProjectDiagnostic.warnings);
        
        boolean show_project_tree=!project_tree.isDetached();
        if(show_project_tree){
            removeFragmentOnUI(project_tree);
            addFragmentOnUI(project_tree);
        }*/
    }
    
    private void printErrors(List<String> list){
        for(String s:list)
        Log.i("eide","E:"+s);
    }
    private void printWarnings(List<String> list){
        for(String s:list)
            Log.i("eide","W:"+s);
    }
}
