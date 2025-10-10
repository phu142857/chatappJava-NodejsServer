import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Card, Space, Table, Tag, Avatar, Form, Input, Divider } from 'antd';
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
  const [form] = Form.useForm();

  const resolveAvatarUrl = (avatar?: string) => {
    if (!avatar) return undefined;
    if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar;
    const base = API_BASE_URL.replace(/\/$/, '');
    // Avatar path from server is already in format /uploads/avatars/filename.jpg
    return `${base}${avatar}`;
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
      <Form form={form} layout="inline" onFinish={async (values) => {
        try {
          if (values.action === 'add') {
            await apiClient.post('/users/admin/friendship', { userId1: values.userId1, userId2: values.userId2 });
            message.success('Friendship added');
          } else {
            await apiClient.delete('/users/admin/friendship', { data: { userId1: values.userId1, userId2: values.userId2 } });
            message.success('Friendship removed');
          }
          form.resetFields();
          fetchData();
        } catch (e: any) {
          message.error(e?.response?.data?.message || 'Operation failed');
        }
      }}>
        <Form.Item name="userId1" rules={[{ required: true, message: 'Enter first user UUID' }]}>
          <Input placeholder="User UUID 1" style={{ width: 240 }} />
        </Form.Item>
        <Form.Item name="userId2" rules={[{ required: true, message: 'Enter second user UUID' }]}>
          <Input placeholder="User UUID 2" style={{ width: 240 }} />
        </Form.Item>
        <Form.Item name="action" initialValue="add">
          <Input hidden />
        </Form.Item>
        <Space>
          <Button type="primary" onClick={() => { form.setFieldValue('action', 'add'); form.submit(); }}>Add Friendship</Button>
          <Button danger onClick={() => { form.setFieldValue('action', 'remove'); form.submit(); }}>Remove Friendship</Button>
        </Space>
      </Form>
      <Divider />
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


