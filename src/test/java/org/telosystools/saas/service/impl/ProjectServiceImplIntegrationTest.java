package org.telosystools.saas.service.impl;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.telosystools.saas.Application;
import org.telosystools.saas.config.MongoConfiguration;
import org.telosystools.saas.domain.Project;

import java.util.List;

/**
 * Created by Adrian on 29/01/15.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@Import(MongoConfiguration.class)
public class ProjectServiceImplIntegrationTest {

    @Autowired
    private ProjectServiceImpl projectService;

    @Test
    public void testIntegration() {
        assertNotNull(projectService);

        Project telosys;
        Project docker;

        telosys = projectService.createProject(new Project("telosys"));
        docker = projectService.createProject(new Project("docker"));

        assertNotNull(telosys);
        assertNotNull(docker);

        List<Project> list = projectService.list();

        assertEquals("2 elements", 2, list.size());
        assertEquals("Name", telosys.getName(), list.get(1).getName());

        Project project = projectService.loadProject(telosys.getName());
        assertEquals("Telosys", telosys.getName(), project.getName());

        projectService.delete(telosys.getName());

        assertEquals("1 element", 1, projectService.list().size());

        projectService.delete(docker.getName());
    }
}