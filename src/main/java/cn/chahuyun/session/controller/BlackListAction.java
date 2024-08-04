package cn.chahuyun.session.controller;

import cn.chahuyun.session.entity.Blacklist;
import cn.chahuyun.session.entity.Scope;
import cn.chahuyun.session.utils.HibernateUtil;
import cn.chahuyun.session.utils.ScopeUtil;
import cn.chahuyun.session.utils.ShareUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.*;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaRoot;

import java.util.List;
import java.util.regex.Pattern;

import static cn.chahuyun.session.HuYanSession.LOGGER;

/**
 * 说明
 *
 * @author Moyuyanli
 * @Description :黑名单工具类
 * @Date 2022/8/24 18:24
 */
public class BlackListAction {


    /**
     * 保存黑名单
     *
     * @param blacklist 黑名单
     * @param scope     作用域
     * @return boolean
     * @author Moyuyanli
     * @date 2022/8/24 23:31
     */
    public static boolean saveBlackList(Blacklist blacklist, Scope scope) {
        try {
            HibernateUtil.factory.fromTransaction(session -> {
                //判断对应作用域是否存在
                session.merge(blacklist);
                return null;
            });
        } catch (Exception e) {
            LOGGER.error("出错啦~");
            return false;
        }
        return true;
    }

    /**
     * 添加黑名单
     *
     * @param event 消息事件
     * @author Moyuyanli
     * @date 2022/8/24 18:34
     */
    public void addBlackList(MessageEvent event) {
        //+hmd:at ...
        MessageChain message = event.getMessage();
        Contact subject = event.getSubject();
        User user = event.getSender();
        String code = message.serializeToMiraiCode();
        Bot bot = event.getBot();

        Group group = null;
        if (subject instanceof Group) {
            group = (Group) subject;
        }

        long userId = 0;
        for (SingleMessage sing : message) {
            if (sing instanceof At) {
                userId = ((At) sing).getTarget();
            }
        }

        String[] split = code.split(" +");
        String reason = "违反规则!";
        boolean kick = true;
        boolean prohibit = true;
        boolean withdraw = true;
        Scope scope = new Scope(bot.getId(), "当前", false, false, subject.getId(), "null");
        if (split.length > 1) {
            for (int i = 1; i < split.length; i++) {
                String s = split[i];
                switch (s) {
                    case "t":
                        kick = false;
                        break;
                    case "jy":
                        prohibit = false;
                        break;
                    case "ch":
                        withdraw = false;
                        break;
                    case "0":
                    case "全局":
                        scope.setScopeName("全局");
                        scope.setGlobal(true);
                        break;
                    case "%":
                        //获取下一次消息
                        subject.sendMessage("请输入封禁理由:");
                        reason = ShareUtils.getNextMessageEventByUser(user).getMessage().serializeToMiraiCode();
                        break;
                    default:
                        if (Pattern.matches("gr[\\dA-z]+", s)) {
                            scope.setScopeName("群组" + s.substring(1));
                            scope.setGroupInfo(true);
                            scope.setListId(s.substring(1));
                        }
                        break;
                }
            }
        }
        Blacklist blacklist = new Blacklist(bot.getId(), userId, reason, kick, prohibit, withdraw, scope);

        if (!saveBlackList(blacklist, scope)) {
            subject.sendMessage("黑名单添加失败!");
        }

        subject.sendMessage("黑名单添加成功!");
        if (group == null || group.getBotPermission() == MemberPermission.MEMBER) {
            return;
        }
        if (blacklist.isKick()) {
            try {
                group.get(userId).kick(reason);
                subject.sendMessage("检测到黑名单用户->" + userId + " 已踢出,理由:" + reason);
            } catch (Exception e) {
                LOGGER.error("出错啦~");
                subject.sendMessage("检测到黑名单用户->" + userId + " 踢出失败!");
            }
        } else {
            if (blacklist.isProhibit()) {
                group.get(userId).mute(999999999);
                subject.sendMessage("检测到黑名单用户->" + userId + " 已禁言,理由:" + reason);
            }
        }
    }

    /**
     * 查询黑名单用户
     *
     * @param event 消息事件
     * @author Moyuyanli
     * @date 2022/8/25 17:13
     */
    public void queryBlackList(MessageEvent event) {
        //hmd:
        Contact subject = event.getSubject();
        Bot bot = event.getBot();

        List<Blacklist> blacklists;
        try {
            blacklists = HibernateUtil.factory.fromTransaction(session -> {
                HibernateCriteriaBuilder builder = session.getCriteriaBuilder();
                JpaCriteriaQuery<Blacklist> query = builder.createQuery(Blacklist.class);
                JpaRoot<Blacklist> from = query.from(Blacklist.class);
                query.select(from);
                query.where(builder.equal(from.get("bot"), bot.getId()));
                List<Blacklist> list = session.createQuery(query).list();
                for (Blacklist blacklist : list) {
                    if (blacklist.getScope() == null) {
                        blacklist.setScope(ScopeUtil.getScope(blacklist.getScopeMark()));
                    }
                }
                return list;
            });
        } catch (Exception e) {
            LOGGER.error("出错啦~");
            return;
        }

        if (blacklists == null || blacklists.isEmpty()) {
            subject.sendMessage("没有黑名单信息!");
            return;
        }

        ForwardMessageBuilder messageBuilder = new ForwardMessageBuilder(subject);
        messageBuilder.add(bot, new PlainText("以下是所有黑名单用户↓"));

        for (Blacklist blacklist : blacklists) {
            MessageChainBuilder chainBuilder = new MessageChainBuilder();
            chainBuilder.add(String.format("黑名单编号:%d%nqq:%d%n封禁理由:%s%n", blacklist.getId(), blacklist.getBlackQQ(), blacklist.getReason()));
            Scope scope = blacklist.getScope();
            if (ShareUtils.mateScope(event, scope)) {
                chainBuilder.add("当前群是否触发:是\n");
            } else {
                chainBuilder.add("当前群是否触发:否\n");
            }
            chainBuilder.add(String.format("作用域:%s", scope.getScopeName()));
            messageBuilder.add(bot, chainBuilder.build());
        }

        subject.sendMessage(messageBuilder.build());

    }

    /**
     * 删除黑名单
     *
     * @param event 消息事件
     * @author Moyuyanli
     * @date 2022/8/25 19:11
     */
    public void deleteBlackList(MessageEvent event) {
        //-hmd:id
        String code = event.getMessage().serializeToMiraiCode();
        Contact subject = event.getSubject();

        String key = code.split("[:：]")[1];

        Boolean aBoolean = HibernateUtil.factory.fromTransaction(session -> {
            HibernateCriteriaBuilder builder = session.getCriteriaBuilder();
            JpaCriteriaQuery<Blacklist> query = builder.createQuery(Blacklist.class);
            JpaRoot<Blacklist> from = query.from(Blacklist.class);
            query.select(from);
            query.where(builder.equal(from.get("id"), key));
            List<Blacklist> list = session.createQuery(query).list();
            if (list == null || list.isEmpty()) {
                return false;
            }
            Blacklist blacklist = list.get(0);
            session.remove(blacklist);
            return true;
        });

        if (aBoolean) {
            subject.sendMessage("黑名单删除成功!");
        } else {
            subject.sendMessage("黑明单删除失败!");
        }
    }

    /**
     * 在消息中处理黑名单用户
     *
     * @param event 消息事件
     * @author Moyuyanli
     * @date 2022/8/25 19:35
     */
    public void isBlackUser(MessageEvent event) {
        Contact subject = event.getSubject();
        Group group;
        if (subject instanceof Group) {
            group = (Group) subject;
        } else {
            return;
        }
        User user = event.getSender();
        Bot bot = event.getBot();
        NormalMember member = group.get(user.getId());

        List<Blacklist> blacklists;
        try {
            blacklists = HibernateUtil.factory.fromTransaction(session -> {
                HibernateCriteriaBuilder builder = session.getCriteriaBuilder();
                JpaCriteriaQuery<Blacklist> query = builder.createQuery(Blacklist.class);
                JpaRoot<Blacklist> from = query.from(Blacklist.class);
                query.select(from);
                query.where(builder.equal(from.get("bot"), bot.getId()));
                query.where(builder.equal(from.get("blackQQ"), user.getId()));
                List<Blacklist> list = session.createQuery(query).list();
                for (Blacklist blacklist : list) {
                    if (blacklist.getScope() == null) {
                        blacklist.setScope(ScopeUtil.getScope(blacklist.getScopeMark()));
                    }
                }
                return list;
            });
        } catch (Exception e) {
            LOGGER.error("出错啦~");
            return;
        }
        if (blacklists == null || blacklists.isEmpty()) {
            return;
        }
        for (Blacklist blacklist : blacklists) {
            if (ShareUtils.mateScope(bot, group, blacklist.getScope())) {
                if (blacklist.isKick()) {
                    try {
                        member.kick("你已被封禁",true);
                        subject.sendMessage(String.format("检测到黑名单用户 %d ,已踢出,封禁理由: %s", user.getId(), blacklist.getReason()));
                    } catch (Exception e) {
                        LOGGER.warning("该用户不存在");
                    }
                } else if (blacklist.isProhibit()) {
                    member.mute(999999999);
                    subject.sendMessage(String.format("检测到黑名单用户 %d ,已禁言,封禁理由: %s", user.getId(), blacklist.getReason()));
                } else if (blacklist.isWithdraw()) {
                    MessageSource.recall(event.getMessage());
                    subject.sendMessage(String.format("检测到黑名单用户 %d ,已撤回消息,封禁理由: %s", user.getId(), blacklist.getReason()));
                }
            }
        }
    }


}
