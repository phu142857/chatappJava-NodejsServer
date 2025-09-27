import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Card, Space, Table, Tag } from 'antd';
import apiClient from '../api/client';

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
          { title: 'From', dataIndex: 'senderId', render: (u) => u?.username || u?._id || '-' },
          { title: 'To', dataIndex: 'receiverId', render: (u) => u?.username || u?._id || '-' },
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


