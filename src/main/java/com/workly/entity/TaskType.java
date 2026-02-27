package com.workly.entity;

public enum TaskType {
    TEXT,
    DOC_TEXT
}

//yes its working fine and super excellent with respect to create a task with file , assigning task to emp, then he can downlod , view tasks , but the thing is in the reassign task u didint understand correctl what i want is the admin can have the both docs which docs are assigned to the emloyees and also the docs already submitted by the employees so the admin can have both like the employee is having the recieved assignment doc and als othe submission doc so this think if we can do in frontend okay then leave it i think you got my point in ui for the employee he can only saw i=his assigned tasks whether the task is text type or the doc type and same on other side the admin side he can have all the tasks pdfs which are assgined or subitted by the employees
// and another thing what i want is the document wenever a doc is attached for suppose admin assigned one task with doc to the employye the employee can recive that doc name should be the task title - name would be taskName+empid when tit comes to submission taskname+submission this wourl be autmatically done oaky do changes accoeding to this