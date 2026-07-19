import { useEffect, useState } from 'react';
import { userApi } from '../api/client';
import { useToast } from '../components/ui/Toast';

export default function UserProfilePage() {
  const { showToast } = useToast();
  const [profile, setProfile] = useState<any>(null);
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [oldPwd, setOldPwd] = useState('');
  const [newPwd, setNewPwd] = useState('');

  const load = async () => {
    try {
      const { data } = await userApi.me();
      setProfile(data);
      setDisplayName(data.displayName || '');
      setEmail(data.email || '');
    } catch (e: any) {
      showToast(e.response?.data?.error || '加载失败', true);
    }
  };

  useEffect(() => { load(); }, []);

  const saveProfile = async () => {
    try {
      await userApi.updateProfile({ displayName, email });
      showToast('资料已更新');
      load();
    } catch (e: any) {
      showToast(e.response?.data?.error || '更新失败', true);
    }
  };

  const savePwd = async () => {
    if (newPwd.length < 6) { showToast('新密码至少 6 位', true); return; }
    try {
      await userApi.changePassword(oldPwd, newPwd);
      showToast('密码已修改');
      setOldPwd(''); setNewPwd('');
    } catch (e: any) {
      showToast(e.response?.data?.error || '修改失败', true);
    }
  };

  return (
    <div>
      <div className="breadcrumb"><span>个人中心</span> / <span>我的资料</span></div>
      <h1>个人资料</h1>

      <div className="profile-grid">
        <div className="panel">
          <div className="panel-head"><h2>基本信息</h2></div>
          <div className="form-row">
            <label>用户名</label>
            <input disabled value={profile?.username || ''} />
          </div>
          <div className="form-row">
            <label>角色</label>
            <input disabled value={profile?.role || ''} />
          </div>
          <div className="form-row">
            <label>显示名</label>
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="显示名称" />
          </div>
          <div className="form-row">
            <label>邮箱</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
          </div>
          <button className="btn btn-primary" onClick={saveProfile}>保存资料</button>
        </div>

        <div className="panel">
          <div className="panel-head"><h2>修改密码</h2></div>
          <div className="form-row">
            <label>原密码</label>
            <input type="password" value={oldPwd} onChange={(e) => setOldPwd(e.target.value)} />
          </div>
          <div className="form-row">
            <label>新密码</label>
            <input type="password" value={newPwd} onChange={(e) => setNewPwd(e.target.value)} placeholder="至少 6 位" />
          </div>
          <button className="btn btn-primary" onClick={savePwd}>更新密码</button>
        </div>
      </div>
    </div>
  );
}
