package cn.chahuyun.utils;

import cn.chahuyun.HuYanSession;
import cn.chahuyun.entity.BlackHouse;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.utils.MiraiLogger;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaRoot;

/**
 * BlackHouseUtil
 * 小黑屋工具类
 *
 * @author Zhangjiaxing
 * @date 2022/8/19 10:48
 */
public class BlackHouseUtil {

    private final static MiraiLogger l = HuYanSession.INSTANCE.getLogger();

    /**
     * 获取小黑屋
     *
     * @param bot bot
     * @param qq  qq
     * @return cn.chahuyun.entity.BlackHouse
     * @author Moyuyanli
     * @date 2022/8/19 11:23
     */
    public static BlackHouse getBlackHouse(Bot bot, long qq) {
        try {
            return HibernateUtil.factory.fromTransaction(session -> {
                HibernateCriteriaBuilder builder = session.getCriteriaBuilder();
                JpaCriteriaQuery<BlackHouse> query = builder.createQuery(BlackHouse.class);
                JpaRoot<BlackHouse> from = query.from(BlackHouse.class);
                query.select(from);
                query.where(builder.equal(from.get("bot"), bot.getId()));
                query.where(builder.equal(from.get("qq"), qq));
                return session.createQuery(query).list().get(0);
            });
        } catch (IndexOutOfBoundsException e) {
            l.warning("暂时没有小黑屋成员!");
            return null;
        } catch (Exception e) {
            l.error("小黑屋查询失败~", e);
        }
        return null;
    }

    /**
     * 更新或保存 小黑屋
     *
     * @param blackHouse 小黑屋
     * @author Moyuyanli
     * @date 2022/8/19 11:18
     */
    public static boolean saveOrUpdate(BlackHouse blackHouse) {
        try {
            return HibernateUtil.factory.fromTransaction(session -> {
                session.merge(blackHouse);
                return true;
            });
        } catch (Exception e) {
            l.error("小黑屋更新失败:", e);
        }
        return false;
    }

}