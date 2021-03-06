package org.telosystools.saas.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.telosystools.saas.Application;
import org.telosystools.saas.config.MongoConfiguration;
import org.telosystools.saas.domain.filesystem.FileData;
import org.telosystools.saas.domain.filesystem.Folder;
import org.telosystools.saas.domain.filesystem.RootFolder;
import org.telosystools.saas.domain.filesystem.Workspace;
import org.telosystools.saas.domain.project.Project;
import org.telosystools.saas.service.ProjectService;
import org.telosystools.saas.service.WorkspaceService;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Adrian
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@Import(MongoConfiguration.class)
public class WorkspaceControllerTest {

    @Inject
    private WorkspaceController workspaceController;

    private WorkspaceService workspaceService;

    @Inject
    private ProjectService projectService;

    private MockMvc mockMvc;

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.standaloneSetup(workspaceController).build();

        final Field wsServiceField = WorkspaceController.class.getDeclaredField("workspaceService");
        wsServiceField.setAccessible(true);
        workspaceService = (WorkspaceService) wsServiceField.get(workspaceController);

        mapper = new ObjectMapper();
    }

    @After
    public void tearDown() throws Exception {
        List<Project> projects = projectService.findAllByUser();
        projects.forEach(e -> projectService.deleteProject(e.getId()));
    }

    /*
   getWorkspace : récupère l'arborescence d'un projet -> contenu json
   */
    @Test
    public void testGetWorkspace() throws Exception {
        // Given
        Project project = new Project();
        project.setName("New Project");
        String projectID = projectService.createProject(project).getId();

        // When
        MvcResult mvcResult = this.mockMvc.perform(get("/projects/" + projectID + "/workspace"))

                // Then
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andReturn();

        String jsonContent = mvcResult.getResponse().getContentAsString();
        Workspace workspace = mapper.readValue(jsonContent, Workspace.class);
        assertNotNull(workspace);
    }

    @Test
    public void testGetWorkspace_NotFound() throws Exception {
        // Given
        String projectID = ObjectId.get().toString();

        // When
        this.mockMvc.perform(get("/projects/" + projectID + "/workspace"))

                // Then
                .andExpect(status().isNotFound());
    }

    /*
      createFile : crée un fichier -> fichier créé status created
    */
    @Test
    public void testCreateFile() throws Exception {
        // Given
        Project project = new Project();
        project.setName("New Project");
        String projectID = projectService.createProject(project).getId();
        String filePath = "model/model_1.xml";
        String fileData = "{\"path\":\"" + filePath + "\", \"content\":\"Contenu du fichier\"}";

        // When
        MvcResult mvcResult = this.mockMvc.perform(post("/projects/" + projectID + "/workspace/files").contentType(MediaType.APPLICATION_JSON).content(fileData))
                // Then
                .andExpect(status().isCreated())
                .andReturn();

        this.mockMvc.perform(post("/projects/" + projectID + "/workspace/files").contentType(MediaType.APPLICATION_JSON).content(fileData))
                // Then
                .andExpect(status().isConflict())
                .andReturn();

        String jsonContent = mvcResult.getResponse().getContentAsString();
        RootFolder rootFolder = mapper.readValue(jsonContent, RootFolder.class);
        assertNotNull(rootFolder);
        assertTrue(rootFolder.getFiles().containsKey("model_1+xml"));
        assertEquals(filePath, rootFolder.getFiles().get("model_1+xml").getAbsolutePath());
    }

    @Test
    public void testCreateFile_ProjectNotFound() throws Exception {
        // Given
        String projectID = ObjectId.get().toString();

        String filePath = "model/model_1.xml";
        String fileData = "{\"path\":\"" + filePath + "\", \"content\":\"Contenu du fichier\"}";

        // When
        this.mockMvc.perform(post("/projects/"+projectID+"/workspace/files").contentType(MediaType.APPLICATION_JSON).content(fileData))

                // Then
                .andExpect(status().isNotFound());
    }

    @Test
    public void testCreateFile_FolderNotFound() throws Exception {
        // Given
        Project project = new Project();
        project.setName("New Project");
        String projectID = projectService.createProject(project).getId();
        String filePath = "model/folder1/model_1.xml";
        String fileData = "{\"path\":\"" + filePath + "\", \"content\":\"Contenu du fichier\"}";

        // When
        this.mockMvc.perform(post("/projects/"+projectID+"/workspace/files").contentType(MediaType.APPLICATION_JSON).content(fileData))

                // Then
                .andExpect(status().isNotFound());
    }

    @Test
    public void testDeleteFile() throws Exception {
        // Given
        Project project = new Project();
        project.setName("new_project");
        String projectID = projectService.createProject(project).getId();

        workspaceService.createFolder("templates/test", projectID);
        workspaceService.createFolder("templates/test/remove", projectID);
        String filePath = "templates/test/remove/test.txt";
        workspaceService.createFile(filePath, "", projectID);
        workspaceService.createFile("templates/test/remove/test2.txt", "", projectID);

        // When
        MvcResult mvcResult = this.mockMvc.perform(delete("/projects/" + projectID + "/workspace/files?path="+filePath).contentType(MediaType.APPLICATION_JSON))
                // Then
                .andExpect(status().isOk())
                .andReturn();

        String jsonContent = mvcResult.getResponse().getContentAsString();

        RootFolder root = mapper.readValue(jsonContent, RootFolder.class);
        // Assert
        Workspace workspace = workspaceService.getWorkspace(projectID);
        Folder removeFolder = workspace.getTemplates().getFolders().get("test").getFolders().get("remove");
        assertNotNull(removeFolder);
        assertTrue(removeFolder.getFiles().containsKey("test2+txt"));
        assertFalse(removeFolder.getFiles().containsKey("test+txt"));
        removeFolder = root.getFolders().get("test").getFolders().get("remove");
        assertNotNull(removeFolder);
        assertTrue(removeFolder.getFiles().containsKey("test2+txt"));
        assertFalse(removeFolder.getFiles().containsKey("test+txt"));
    }

    @Test
    public void testRenameFile() throws Exception {
        // Given
        Project project = new Project();
        project.setName("new_project");
        String projectID = projectService.createProject(project).getId();

        workspaceService.createFolder("templates/rename", projectID);
        workspaceService.createFolder("templates/rename/test", projectID);
        final String filePath = "templates/rename/test/test.txt";
        workspaceService.createFile(filePath, "content", projectID);

        String fileData = "{\"path\":\"" + filePath + "\", \"name\":\"renamedFile.pdf\"}";
        // When
        MvcResult mvcResult = this.mockMvc.perform(patch("/projects/" + projectID + "/workspace/files").contentType(MediaType.APPLICATION_JSON).content(fileData))
                // Then
                .andExpect(status().isOk())
                .andReturn();
        String jsonContent = mvcResult.getResponse().getContentAsString();

        // Assert
        Folder parentFolder = workspaceService.getWorkspace(projectID).getTemplates().getFolders().get("rename").getFolders().get("test");
        assertTrue(parentFolder.getFiles().containsKey("renamedFile+pdf"));
        assertEquals("content", workspaceService.getFileContent("templates/rename/test/renamedFile.pdf", projectID).getContent());
        assertEquals("templates/rename/test/renamedFile.pdf", parentFolder.getFiles().get("renamedFile+pdf").getAbsolutePath());
        final RootFolder root = mapper.readValue(jsonContent, RootFolder.class);
        parentFolder = root.getFolders().get("rename").getFolders().get("test");
        assertTrue(parentFolder.getFiles().containsKey("renamedFile+pdf"));
        assertEquals("content", workspaceService.getFileContent("templates/rename/test/renamedFile.pdf", projectID).getContent());
        assertEquals("templates/rename/test/renamedFile.pdf", parentFolder.getFiles().get("renamedFile+pdf").getAbsolutePath());
    }

    /*
     * createFolder : crée un folder -> Folder créé status created
     */
    @Test
    public void testCreateFolder() throws Exception {

        // Given
        Project project = new Project();
        project.setName("new_project");
        String projectID = projectService.createProject(project).getId();

        String folderPath = "templates/test";
        String fileData = "{\"path\":\"" + folderPath + "\"}";

        // When
        MvcResult mvcResult = this.mockMvc.perform(post("/projects/"+projectID+"/workspace/folders").contentType(MediaType.APPLICATION_JSON).content(fileData))
                // Then
                .andExpect(status().isCreated())
                .andReturn();

        // Assert
        String jsonConent = mvcResult.getResponse().getContentAsString();
        RootFolder rootFolder = mapper.readValue(jsonConent, RootFolder.class);
        // Check filesystem tree
        assertNotNull(rootFolder);
        assertEquals("templates", rootFolder.getAbsolutePath());
        assertEquals(folderPath, rootFolder.getFolders().get("test").getAbsolutePath());

        // Try to create same folder
        // When
        this.mockMvc.perform(post("/projects/"+projectID+"/workspace/folders").contentType(MediaType.APPLICATION_JSON).content(fileData))
                // Then
                .andExpect(status().isConflict())
                .andReturn();

        // Creation of a sub-folder
        folderPath = "templates/test/createFolder";
        fileData = "{\"path\":\"" + folderPath + "\"}";

        mvcResult = this.mockMvc.perform(post("/projects/"+projectID+"/workspace/folders").contentType(MediaType.APPLICATION_JSON).content(fileData))
                // Then
                .andExpect(status().isCreated())
                .andReturn();

        // Assert
        jsonConent = mvcResult.getResponse().getContentAsString();
        rootFolder = mapper.readValue(jsonConent, RootFolder.class);
        assertNotNull(rootFolder);
        assertEquals(folderPath, rootFolder.getFolders()
                .get("test").getFolders().get("createFolder").getAbsolutePath());
    }

    @Test
    public void testDeleteFolder() throws Exception {
        // Given
        Project project = new Project();
        project.setName("new_project");
        String projectID = projectService.createProject(project).getId();

        workspaceService.createFolder("templates/test", projectID);
        final String folderPath = "templates/test/remove";
        workspaceService.createFolder(folderPath, projectID);
        workspaceService.createFile("templates/test/remove/test.txt", "", projectID);
        workspaceService.createFile("templates/test/testParent.txt", "", projectID);

        // Delete folder
        MvcResult mvcResult = this.mockMvc.perform(delete("/projects/" + projectID + "/workspace/folders?path="+folderPath))
                // Then
                .andExpect(status().isOk())
                .andReturn();
        String jsonContent = mvcResult.getResponse().getContentAsString();

        // Assert
        RootFolder root = workspaceService.getWorkspace(projectID).getTemplates();
        assertTrue(root.getFolders().get("test").getFiles().containsKey("testParent+txt"));
        assertTrue(root.getFolders().get("test").getFolders().isEmpty());
        root = mapper.readValue(jsonContent, RootFolder.class);
        assertTrue(root.getFolders().get("test").getFiles().containsKey("testParent+txt"));
        assertTrue(root.getFolders().get("test").getFolders().isEmpty());
    }

    @Test
    public void testRenameFolder() throws Exception {
        // Given
        Project project = new Project();
        project.setName("new_project");
        String projectID = projectService.createProject(project).getId();

        workspaceService.createFolder("templates/rename", projectID);
        final String folderPath = "templates/rename";
        workspaceService.createFolder("templates/rename/test", projectID);
        workspaceService.createFile("templates/rename/test/test.txt", "", projectID);
        workspaceService.createFile("templates/rename/testParent.txt", "", projectID);

        String fileData = "{\"path\":\"" + folderPath + "\", \"name\":\"foo\"}";

        // When
        final String url = "/projects/" + projectID + "/workspace/folders";
        MvcResult mvcResult = this.mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(fileData))
                // Then
                .andExpect(status().isOk())
                .andReturn();
        String jsonContent = mvcResult.getResponse().getContentAsString();

        // Assert
        Folder foo = workspaceService.getWorkspace(projectID).getTemplates().getFolders().get("foo");
        assertNotNull(foo);
        assertEquals("templates/foo/test", foo.getFolders().get("test").getAbsolutePath());
        assertTrue(foo.getFiles().containsKey("testParent+txt"));
        assertEquals("templates/foo/test/test.txt", foo.getFolders().get("test").getFiles().get("test+txt").getAbsolutePath());
        RootFolder root = mapper.readValue(jsonContent, RootFolder.class);
        foo = root.getFolders().get("foo");
        assertNotNull(foo);
        assertEquals("templates/foo/test", foo.getFolders().get("test").getAbsolutePath());
        assertTrue(foo.getFiles().containsKey("testParent+txt"));
        assertEquals("templates/foo/test/test.txt", foo.getFolders().get("test").getFiles().get("test+txt").getAbsolutePath());
    }

    /*
    getFileContent : récupère le contenu d'un fichier -> retourne une chaîne de caractère status ok
    */
    @Test
    public void testGetFileContent() throws Exception {
        // Given
        Project project = new Project();
        project.setName("New Project");
        String projectID = projectService.createProject(project).getId();
        String expectedContent = "Contenu du fichier";
        workspaceService.createFile("model/model_2.xml", expectedContent, projectID);

        // When
        final String url = "/projects/" + projectID + "/workspace/files?path=model/model_2.xml";
        String jsonContent = this.mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON))

                // Then
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        FileData data = mapper.readValue(jsonContent, FileData.class);
        assertEquals(expectedContent, data.getContent());
    }

    @Test
    public void testGetFileContent_NotFound() throws Exception {
        // Given
        Project project = new Project();
        project.setName("New Project");
        String projectID = projectService.createProject(project).getId();
        String fileID = ObjectId.get().toString();

        // When
        this.mockMvc.perform(get("/projects/" + projectID + "/workspace/files/" + fileID))

                // Then
                .andExpect(status().isNotFound());
    }

    /*
   updateFile : change le contenu d'un fichier -> status ok
   */
    @Test
    public void testUpdateFile() throws Exception {
        // Given
        Project project = new Project();
        project.setName("New Project");
        String projectID = projectService.createProject(project).getId();

        String filePath = "model/model_2.xml";

        workspaceService.createFile(filePath, "", projectID);

        String fileContent = "content";
        String fileData = "{\"path\":\"" + filePath + "\", \"content\":\"" + fileContent + "\"}";

        // When
        final String url = "/projects/" + projectID + "/workspace/files";
        this.mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(fileData))
                // Then
                .andExpect(status().isOk());

        FileData newFile = workspaceService.getFileContent(filePath, projectID);
        assertNotNull(newFile);
        assertEquals(fileContent, newFile.getContent());
    }


    @Test
    public void testUpdateFile_ProjectNotFound() throws Exception {
        // Given
        String projectID = ObjectId.get().toString();

        String filePath = "model/model_2.xml";

        String fileContent = "content";
        String fileData = "{\"path\":\"" + filePath + "\", \"content\":\"" + fileContent + "\"}";

        // When
        final String url = "/projects/" + projectID + "/workspace/files";
        this.mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(fileData))

                // Then
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUpdateFile_FileNotFound() throws Exception {
        // Given
        Project project = new Project();
        project.setName("New Project");
        String projectID = projectService.createProject(project).getId();

        String filePath = "model/model_2.xml";

        String fileContent = "content";
        String fileData = "{\"path\":\"" + filePath + "\", \"content\":\"" + fileContent + "\"}";

        // When
        final String url = "/projects/" + projectID + "/workspace/files";
        this.mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(fileData))

                // Then
                .andExpect(status().isNotFound());
    }

}