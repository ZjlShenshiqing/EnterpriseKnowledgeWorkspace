package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysTodo;
import com.zjl.collaboration.mapper.SysTodoMapper;
import com.zjl.collaboration.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 待办业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService {

    private final SysTodoMapper todoMapper;

    @Override
    public List<SysTodo> list(Long userId) {
        return todoMapper.selectList(Wrappers.lambdaQuery(SysTodo.class)
                .eq(SysTodo::getUserId, userId)
                .orderByAsc(SysTodo::getDone)
                .orderByAsc(SysTodo::getDueDate));
    }

    @Override
    public Long create(String title, String priority, Date dueDate, Long userId) {
        SysTodo todo = new SysTodo();
        todo.setTitle(title);
        todo.setUserId(userId);
        todo.setPriority(priority);
        todo.setDueDate(dueDate);
        todo.setDone(0);
        todoMapper.insert(todo);
        log.info("待办创建: userId={}, todoId={}", userId, todo.getId());
        return todo.getId();
    }

    @Override
    public void update(Long id, String title, String priority, Date dueDate) {
        SysTodo todo = todoMapper.selectById(id);
        if (todo == null) {
            return;
        }
        todo.setTitle(title);
        todo.setPriority(priority);
        todo.setDueDate(dueDate);
        todoMapper.updateById(todo);
    }

    @Override
    public void toggle(Long id) {
        SysTodo todo = todoMapper.selectById(id);
        if (todo == null) {
            return;
        }
        todo.setDone(todo.getDone() == 1 ? 0 : 1);
        todoMapper.updateById(todo);
    }

    @Override
    public void delete(Long id) {
        todoMapper.deleteById(id);
    }
}
