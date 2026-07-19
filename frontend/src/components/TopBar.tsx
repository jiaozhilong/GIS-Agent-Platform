import { useEffect, useRef, useState } from 'react';
import { notificationApi } from '../api/client';
import { IconBell } from './ui/icons';
import { useToast } from './ui/Toast';

interface NotifItem {
  id: number;
  type: string | null;
  title: string;
  body: string | null;
  link: string | null;
  isRead: boolean;
  createdAt: string | null;
}

export default function TopBar() {
  const { showToast } = useToast();
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<NotifItem[]>([]);
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  const load = async () => {
    try {
      const { data } = await notificationApi.summary();
      setUnread(data.unread || 0);
      setItems(data.items || []);
    } catch {
      /* 静默失败，顶栏不应阻塞页面 */
    }
  };

  useEffect(() => { load(); }, []);

  // 点击外部关闭下拉
  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  const readAll = async () => {
    try {
      await notificationApi.readAll();
      setUnread(0);
      setItems((prev) => prev.map((i) => ({ ...i, isRead: true })));
      showToast('已全部标为已读');
    } catch (e: any) {
      showToast(e.response?.data?.error || '操作失败', true);
    }
  };

  return (
    <header className="topbar">
      <div className="topbar-spacer" />
      <div className="notif-wrap" ref={wrapRef}>
        <button
          className="notif-bell"
          onClick={() => { setOpen((v) => !v); if (!open) load(); }}
          aria-label="通知中心"
        >
          <IconBell width={20} height={20} />
          {unread > 0 && <span className="notif-badge">{unread > 99 ? '99+' : unread}</span>}
        </button>

        {open && (
          <div className="notif-panel">
            <div className="notif-head">
              <span>通知中心</span>
              <button className="notif-readall" onClick={readAll} disabled={unread === 0}>
                全部已读
              </button>
            </div>
            <div className="notif-list">
              {items.length === 0 ? (
                <div className="notif-empty">暂无通知</div>
              ) : (
                items.map((n) => (
                  <div key={n.id} className={`notif-item ${n.isRead ? '' : 'unread'}`}>
                    <div className="notif-title">{n.title}</div>
                    {n.body && <div className="notif-body">{n.body}</div>}
                    <div className="notif-time muted">
                      {n.createdAt?.slice(0, 19)?.replace('T', ' ')}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </header>
  );
}
