import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Card, Space, Table, Tag, Avatar } from 'antd';
import apiClient from '../api/client';
import { API_BASE_URL } from '../config';

type FriendRequest = {
  _id: string;
  senderId: any;
  receiverId: any;
  status: 'pending' | 'accepted' | 'rejected';
  createdAt?: string;
};

export default function FriendRequests() {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<FriendRequest[]>([]);

  const resolveAvatarUrl = (avatar?: string) => {
    if (!avatar) return undefined;
    if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar;
    const base = API_BASE_URL.replace(/\/$/, '');
    const path = avatar.startsWith('/') ? avatar : `/${avatar}`;
    return `${base}${path}`;
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get<{ success: boolean; data: { requests: FriendRequest[] } }>(
        '/friend-requests'
      );
      setData(data?.data?.requests || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  return (
    <Card title="Friend Requests">
      <Table
        rowKey="_id"
        loading={loading}
        dataSource={data}
        columns={[
          { 
            title: 'From', 
            dataIndex: 'senderId', 
            render: (u) => (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size="small" src={resolveAvatarUrl(u?.avatar)} icon={<span>{u?.username?.[0]?.toUpperCase()}</span>} />
                <span>{u?.username || u?._id || '-'}</span>
              </div>
            )
          },
          { 
            title: 'To', 
            dataIndex: 'receiverId', 
            render: (u) => (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size="small" src={resolveAvatarUrl(u?.avatar)} icon={<span>{u?.username?.[0]?.toUpperCase()}</span>} />
                <span>{u?.username || u?._id || '-'}</span>
              </div>
            )
          },
          { title: 'Status', dataIndex: 'status', render: (s) => <Tag color={s === 'pending' ? 'gold' : s === 'accepted' ? 'green' : 'red'}>{s}</Tag> },
          {
            title: 'Actions',
            key: 'actions',
            render: (_, r) => (
              <Space>
                <Button size="small" type="primary" onClick={async () => {
                  await apiClient.put(`/friend-requests/${r._id}`, { action: 'accept' });
                  message.success('Accepted');
                  fetchData();
                }}>Accept</Button>
                <Button size="small" danger onClick={async () => {
                  await apiClient.put(`/friend-requests/${r._id}`, { action: 'reject' });
                  message.success('Declined');
                  fetchData();
                }}>Decline</Button>
              </Space>
            )
          }
        ]}
      />
    </Card>
  );
}


