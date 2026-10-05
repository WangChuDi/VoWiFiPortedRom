#include <linux/xfrm.h>
#include <linux/in6.h>
#define AF_INET 2
typedef unsigned int uint32_t;
extern int setsockopt(int, int, int, const void *, unsigned int);
extern int getsockopt(int, int, int, void *, unsigned int *);
extern int *__errno(void);

/* Only the caller's socket IN policy is changed. No netlink/global policy operations. */
__attribute__((visibility("default")))
int Java_ImsNestedPolicy_install(void *env, void *klass, int fd,
        int inner_src, int inner_dst, int inner_spi,
        int outer_src, int outer_dst, int outer_spi, int reqid) {
    struct {
        struct xfrm_userpolicy_info info;
        struct xfrm_user_tmpl templates[2];
    } policy = {0};
    policy.info.sel.family = AF_INET;
    policy.info.dir = XFRM_POLICY_IN;
    policy.info.action = XFRM_POLICY_ALLOW;
    policy.info.share = XFRM_SHARE_ANY;
    policy.info.flags = XFRM_POLICY_LOCALOK;
    policy.info.lft.soft_byte_limit = XFRM_INF;
    policy.info.lft.hard_byte_limit = XFRM_INF;
    policy.info.lft.soft_packet_limit = XFRM_INF;
    policy.info.lft.hard_packet_limit = XFRM_INF;
    for (int i=0;i<2;i++) {
        policy.templates[i].family = AF_INET;
        policy.templates[i].id.proto = 50; /* ESP */
        policy.templates[i].aalgos = ~0U;
        policy.templates[i].ealgos = ~0U;
        policy.templates[i].calgos = ~0U;
    }
    policy.templates[0].mode = XFRM_MODE_TRANSPORT;
    policy.templates[0].reqid = reqid;
    policy.templates[0].saddr.a4 = __builtin_bswap32((uint32_t)inner_src);
    policy.templates[0].id.daddr.a4 = __builtin_bswap32((uint32_t)inner_dst);
    policy.templates[0].id.spi = __builtin_bswap32((uint32_t)inner_spi);
    policy.templates[1].mode = XFRM_MODE_TUNNEL;
    policy.templates[1].reqid = 0;
    policy.templates[1].saddr.a4 = __builtin_bswap32((uint32_t)outer_src);
    policy.templates[1].id.daddr.a4 = __builtin_bswap32((uint32_t)outer_dst);
    policy.templates[1].id.spi = __builtin_bswap32((uint32_t)outer_spi);
    int family=0; unsigned int size=sizeof(family);
    if(getsockopt(fd,1,39,&family,&size))return -*__errno(); /* SO_DOMAIN */
    if(family!=2 && family!=10)return -97;
    /* Java uses a dual-stack AF_INET6 socket even for IPv4 peers. */
    return setsockopt(fd, family==10?41:0, family==10?IPV6_XFRM_POLICY:17, &policy, sizeof(policy)) ? -*__errno() : 0;
}
